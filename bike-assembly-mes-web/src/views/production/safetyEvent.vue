<template>
  <PageContainer title="安全事件" description="登记、跟踪与关闭安全生产事件。">
    <!-- 搜索栏（在当前已加载数据中筛选） -->
    <div class="search-bar">
      <el-input v-model="searchForm.eventType" placeholder="事件类型（本地筛选）" clearable />
      <el-input v-model="searchForm.responsibleUser" placeholder="责任人（本地筛选）" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-actions">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增安全事件
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="eventId" label="ID" width="60" class-name="mono-num" />
      <el-table-column prop="parentId" label="父级 ID" width="70" class-name="mono-num">
        <template #default="{ row }">
          <span :class="{ 'text-muted': !row.parentId }">
            {{ row.parentId ?? '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="eventType" label="事件类型" width="100">
        <template #default="{ row }">
          <el-tag
            :type="row.eventType === '人员伤害' ? 'danger' : row.eventType === '设备事故' ? 'warning' : 'info'"
            size="small"
          >
            {{ row.eventType || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="事件标题" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.title }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="level" label="风险等级" width="90">
        <template #default="{ row }">
          <el-tag
            :type="row.level === '特别重大' ? 'danger' : row.level === '重大' ? 'warning' : row.level === '较大' ? '' : 'success'"
            size="small"
          >
            {{ row.level || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="responsibleUser" label="责任人" width="100" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
            {{ row.status === '0' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="eventTime" label="发生时间" width="150" class-name="mono-num" />
      <el-table-column prop="closeTime" label="关闭时间" width="150" class-name="mono-num">
        <template #default="{ row }">
          <span :class="{ 'text-muted': !row.closeTime }">
            {{ row.closeTime || '未关闭' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="130" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button type="primary" link size="small" title="编辑安全事件" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该安全事件吗？" confirm-button-type="danger" @confirm="handleDelete(row.eventId)">
              <template #reference>
                <el-button type="danger" link size="small" title="删除安全事件">删除</el-button>
              </template>
            </el-popconfirm>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无数据" :image-size="72" />
      </template>
    </el-table>

    <!-- V2.3 分页 -->
    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[15, 30, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="fetchList"
        @size-change="fetchList"
      />
    </div>
    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑安全事件' : '新增安全事件'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="父级 ID" prop="parentId">
          <el-input-number
            v-model="form.parentId"
            :min="0"
            placeholder="0 表示顶级事件（选填）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="事件类型" prop="eventType">
          <el-select v-model="form.eventType" placeholder="请选择事件类型" style="width: 100%">
            <el-option label="人员伤害" value="人员伤害" />
            <el-option label="设备事故" value="设备事故" />
            <el-option label="环境事件" value="环境事件" />
            <el-option label="未遂事件" value="未遂事件" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="事件标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入事件标题" />
        </el-form-item>
        <el-form-item label="风险等级" prop="level">
          <el-select v-model="form.level" placeholder="请选择风险等级" style="width: 100%">
            <el-option label="一般" value="一般" />
            <el-option label="较大" value="较大" />
            <el-option label="重大" value="重大" />
            <el-option label="特别重大" value="特别重大" />
          </el-select>
        </el-form-item>
        <el-form-item label="责任人" prop="responsibleUser">
          <el-input v-model="form.responsibleUser" placeholder="请输入责任人" />
        </el-form-item>
        <el-form-item label="发生时间" prop="eventTime">
          <el-date-picker
            v-model="form.eventTime"
            type="datetime"
            placeholder="请选择发生时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="关闭时间" prop="closeTime">
          <el-date-picker
            v-model="form.closeTime"
            type="datetime"
            placeholder="请选择关闭时间（选填）"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">启用</el-radio>
            <el-radio value="1">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import PageContainer from '@/components/PageContainer.vue'
import { getSafSafetyEventList, createSafSafetyEvent, updateSafSafetyEvent, deleteSafSafetyEvent } from '@/api/safSafetyEvent'
import type { SafSafetyEvent } from '@/types/api'

const tableData = ref<SafSafetyEvent[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editId = ref<number>()

const searchForm = reactive({
  eventType: '',
  responsibleUser: ''
})
const query = reactive({ page: 1, size: 15 })
const total = ref(0)

const form = reactive<SafSafetyEvent>({
  parentId: undefined,
  eventType: '',
  title: '',
  level: '',
  responsibleUser: '',
  status: '0',
  eventTime: '',
  closeTime: ''
})

/** 新增和编辑校验规则分开定义，支持后续差异化 */
function getFormRules(_isEdit: boolean): FormRules {
  return {
    // parentId: 新增和编辑都选填（DB BIGINT NULL，0 或空表示顶级事件）
    eventType: [{ required: true, message: '请选择事件类型', trigger: 'change' }],
    title: [{ required: true, message: '请输入事件标题', trigger: 'blur' }],
    level: [{ required: true, message: '请选择风险等级', trigger: 'change' }],
    responsibleUser: [{ required: true, message: '请输入责任人', trigger: 'blur' }],
    eventTime: [{ required: true, message: '请选择发生时间', trigger: 'change' }],
    // closeTime: 新增和编辑都选填（DB DATETIME NULL，事件可能尚未关闭）
    status: [{ required: true, message: '请选择状态', trigger: 'change' }]
  }
}

const rules = ref<FormRules>(getFormRules(false))

onMounted(() => {
  fetchList()
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getSafSafetyEventList({ page: query.page, size: query.size })
    total.value = res?.total ?? 0
    const allData: SafSafetyEvent[] = Array.isArray(res) ? res : (res?.records || [])
    const type = searchForm.eventType.trim().toLowerCase()
    const user = searchForm.responsibleUser.trim().toLowerCase()
    tableData.value = allData.filter(item => {
      const matchType = !type || (item.eventType || '').toLowerCase().includes(type)
      const matchUser = !user || (item.responsibleUser || '').toLowerCase().includes(user)
      return matchType && matchUser
    })
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { loading.value = false }
}


function handleSearch() {
  query.page = 1
  fetchList()
}

function resetSearch() {
  query.page = 1
  searchForm.eventType = ''
  searchForm.responsibleUser = ''
  fetchList()
}

function resetForm() {
  form.parentId = undefined
  form.eventType = ''
  form.title = ''
  form.level = ''
  form.responsibleUser = ''
  form.status = '0'
  form.eventTime = ''
  form.closeTime = ''
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  resetForm()
  rules.value = getFormRules(false)
  dialogVisible.value = true
}

function handleEdit(row: SafSafetyEvent) {
  isEdit.value = true
  editId.value = row.eventId
  form.parentId = row.parentId
  form.eventType = row.eventType
  form.title = row.title
  form.level = row.level
  form.responsibleUser = row.responsibleUser
  form.status = row.status
  form.eventTime = row.eventTime || ''
  form.closeTime = row.closeTime || ''
  rules.value = getFormRules(true)
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateSafSafetyEvent(editId.value, { ...form })
      ElMessage.success('编辑成功')
    } else {
      await createSafSafetyEvent({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { submitting.value = false }
}

async function handleDelete(id: number) {
  try {
    await deleteSafSafetyEvent(id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}
</script>

<style scoped>
/* 工具条仅承载主操作：右对齐；窄屏由全局工具条规则自动换行 */
.table-toolbar {
  justify-content: flex-end;
}
</style>
