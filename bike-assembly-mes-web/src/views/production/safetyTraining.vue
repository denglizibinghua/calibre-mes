<template>
  <PageContainer title="安全培训" description="维护安全培训计划与参与记录。">
    <!-- 搜索栏（在当前已加载数据中筛选） -->
    <div class="search-bar">
      <el-input v-model="searchForm.trainingTitle" placeholder="培训主题（本地筛选）" clearable />
      <el-input v-model="searchForm.trainingType" placeholder="培训类型（本地筛选）" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-actions">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增安全培训
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="trainingId" label="ID" width="60" class-name="mono-num" />
      <el-table-column prop="trainingTitle" label="培训主题" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.trainingTitle }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="trainingType" label="培训类型" width="100">
        <template #default="{ row }">
          <el-tag
            :type="row.trainingType === '安全培训' ? 'danger' : row.trainingType === '技能培训' ? 'success' : row.trainingType === '应急演练' ? 'warning' : 'info'"
            size="small"
          >
            {{ row.trainingType || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="trainer" label="讲师" width="90" show-overflow-tooltip />
      <el-table-column prop="startTime" label="开始时间" width="150" class-name="mono-num" />
      <el-table-column prop="endTime" label="结束时间" width="150" class-name="mono-num">
        <template #default="{ row }">
          <span :class="{ 'text-muted': !row.endTime }">
            {{ row.endTime || '进行中' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="usersJson" label="参训人员" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ 'text-muted': !row.usersJson }">{{ row.usersJson || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
            {{ row.status === '0' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="140" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button type="primary" link size="small" title="编辑安全培训" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该安全培训吗？" confirm-button-type="danger" @confirm="handleDelete(row.trainingId)">
              <template #reference>
                <el-button type="danger" link size="small" title="删除安全培训">删除</el-button>
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
      :title="isEdit ? '编辑安全培训' : '新增安全培训'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="培训主题" prop="trainingTitle">
          <el-input v-model="form.trainingTitle" placeholder="请输入培训主题" />
        </el-form-item>
        <el-form-item label="培训类型" prop="trainingType">
          <el-select v-model="form.trainingType" placeholder="请选择培训类型" style="width: 100%">
            <el-option label="安全培训" value="安全培训" />
            <el-option label="技能培训" value="技能培训" />
            <el-option label="应急演练" value="应急演练" />
            <el-option label="入职培训" value="入职培训" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="讲师" prop="trainer">
          <el-input v-model="form.trainer" placeholder="请输入讲师姓名" />
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            placeholder="请选择开始时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker
            v-model="form.endTime"
            type="datetime"
            placeholder="请选择结束时间（选填）"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="参训人员 JSON" prop="usersJson">
          <el-input
            v-model="form.usersJson"
            type="textarea"
            :rows="4"
            placeholder='请输入参训人员 JSON，如 [{"userId":1,"name":"张三"}]'
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
import { getSafTrainingList, createSafTraining, updateSafTraining, deleteSafTraining } from '@/api/safTraining'
import type { SafTraining } from '@/types/api'

const tableData = ref<SafTraining[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editId = ref<number>()

const searchForm = reactive({
  trainingTitle: '',
  trainingType: ''
})
const query = reactive({ page: 1, size: 15 })
const total = ref(0)

const form = reactive<SafTraining>({
  trainingTitle: '',
  trainingType: '',
  trainer: '',
  startTime: '',
  endTime: '',
  usersJson: '',
  status: '0'
})

/** 新增和编辑校验规则分开定义，支持后续差异化 */
function getFormRules(_isEdit: boolean): FormRules {
  return {
    trainingTitle: [{ required: true, message: '请输入培训主题', trigger: 'blur' }],
    trainingType: [{ required: true, message: '请选择培训类型', trigger: 'change' }],
    trainer: [{ required: true, message: '请输入讲师', trigger: 'blur' }],
    startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
    // endTime: 新增和编辑都选填（DB DATETIME NULL，培训可能尚未结束）
    usersJson: [{ required: true, message: '请输入参训人员 JSON', trigger: 'blur' }],
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
    const res = await getSafTrainingList({ page: query.page, size: query.size })
    total.value = res?.total ?? 0
    const allData: SafTraining[] = Array.isArray(res) ? res : (res?.records || [])
    const title = searchForm.trainingTitle.trim().toLowerCase()
    const type = searchForm.trainingType.trim().toLowerCase()
    tableData.value = allData.filter(item => {
      const matchTitle = !title || (item.trainingTitle || '').toLowerCase().includes(title)
      const matchType = !type || (item.trainingType || '').toLowerCase().includes(type)
      return matchTitle && matchType
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
  searchForm.trainingTitle = ''
  searchForm.trainingType = ''
  fetchList()
}

function resetForm() {
  form.trainingTitle = ''
  form.trainingType = ''
  form.trainer = ''
  form.startTime = ''
  form.endTime = ''
  form.usersJson = ''
  form.status = '0'
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  resetForm()
  rules.value = getFormRules(false)
  dialogVisible.value = true
}

function handleEdit(row: SafTraining) {
  isEdit.value = true
  editId.value = row.trainingId
  form.trainingTitle = row.trainingTitle
  form.trainingType = row.trainingType
  form.trainer = row.trainer
  form.startTime = row.startTime || ''
  form.endTime = row.endTime || ''
  form.usersJson = row.usersJson || ''
  form.status = row.status
  rules.value = getFormRules(true)
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateSafTraining(editId.value, { ...form })
      ElMessage.success('编辑成功')
    } else {
      await createSafTraining({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { submitting.value = false }
}

async function handleDelete(id: number) {
  try {
    await deleteSafTraining(id)
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
