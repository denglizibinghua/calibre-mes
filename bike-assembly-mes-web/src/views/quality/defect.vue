<template>
  <PageContainer title="缺陷管理" description="维护缺陷类型、等级与处理结果。">
    <!-- 搜索栏（在当前已加载数据中筛选） -->
    <div class="search-bar">
      <el-input v-model="searchForm.defectType" placeholder="缺陷类型（本地筛选）" clearable />
      <el-input v-model="searchForm.handleResult" placeholder="处理结果（本地筛选）" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-actions">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增缺陷记录
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="defectId" label="ID" width="60" class-name="mono-num" />
      <el-table-column prop="inspectionId" label="质检单 ID" width="90" class-name="mono-num" />
      <el-table-column prop="workOrderId" label="工单 ID" width="90" class-name="mono-num" />
      <el-table-column prop="processId" label="工序 ID" width="90" class-name="mono-num" />
      <el-table-column prop="defectType" label="缺陷类型" width="120" show-overflow-tooltip />
      <el-table-column prop="qty" label="数量" width="80" class-name="mono-num" />
      <el-table-column prop="handleResult" label="处理结果" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ 'text-muted': !row.handleResult }">
            {{ row.handleResult || '待处理' }}
          </span>
        </template>
      </el-table-column>
      <!-- qms-defect 的 status 是缺陷处理状态，不是启用/禁用: 0=待处理 1=处理中 2=已闭环 -->
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="defectStatusTagType(row.status)" size="small">
            {{ defectStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="160" class-name="mono-num" />
      <el-table-column label="操作" min-width="130" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button type="primary" link size="small" title="编辑缺陷记录" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该缺陷记录吗？" confirm-button-type="danger" @confirm="handleDelete(row.defectId)">
              <template #reference>
                <el-button type="danger" link size="small" title="删除缺陷记录">删除</el-button>
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
      :title="isEdit ? '编辑缺陷记录' : '新增缺陷记录'"
      width="540px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="质检单 ID" prop="inspectionId">
          <el-input-number
            v-model="form.inspectionId"
            :min="1"
            placeholder="请输入质检单 ID（选填）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="工单 ID" prop="workOrderId">
          <el-input-number
            v-model="form.workOrderId"
            :min="1"
            placeholder="请输入工单 ID"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="工序 ID" prop="processId">
          <el-input-number
            v-model="form.processId"
            :min="1"
            placeholder="请输入工序 ID（选填）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="缺陷类型" prop="defectType">
          <el-select v-model="form.defectType" placeholder="请选择缺陷类型" style="width: 100%">
            <el-option label="外观缺陷" value="外观缺陷" />
            <el-option label="尺寸不良" value="尺寸不良" />
            <el-option label="装配不良" value="装配不良" />
            <el-option label="功能不良" value="功能不良" />
            <el-option label="材料不良" value="材料不良" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量" prop="qty">
          <el-input-number
            v-model="form.qty"
            :min="0"
            :precision="4"
            placeholder="请输入不合格品数量"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="处理结果" prop="handleResult">
          <el-input v-model="form.handleResult" placeholder="请输入处理结果（选填）" />
        </el-form-item>
        <!-- qms-defect status 是缺陷处理状态，不是启用/禁用: 0=待处理 1=处理中 2=已闭环 -->
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
            <el-option label="待处理" value="0" />
            <el-option label="处理中" value="1" />
            <el-option label="已闭环" value="2" />
          </el-select>
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
import { getQmsDefectList, createQmsDefect, updateQmsDefect, deleteQmsDefect } from '@/api/qmsDefect'
import type { QmsDefect } from '@/types/api'

const tableData = ref<QmsDefect[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editId = ref<number>()

const searchForm = reactive({
  defectType: '',
  handleResult: ''
})
const query = reactive({ page: 1, size: 15 })
const total = ref(0)

const form = reactive<QmsDefect>({
  inspectionId: undefined,
  workOrderId: undefined,
  processId: undefined,
  defectType: '',
  qty: 0,
  handleResult: '',
  status: '0'
})

/** 新增和编辑校验规则分开定义，支持后续差异化 */
function getFormRules(_isEdit: boolean): FormRules {
  return {
    // inspectionId: 新增和编辑都选填（DB BIGINT NULL）
    workOrderId: [{ required: true, message: '请输入工单 ID', trigger: 'change' }],
    // processId: 新增和编辑都选填（DB BIGINT NULL）
    defectType: [{ required: true, message: '请选择缺陷类型', trigger: 'change' }],
    qty: [{ required: true, message: '请输入数量', trigger: 'change' }],
    // handleResult: 新增和编辑都选填（DB VARCHAR(255) NULL）
    status: [{ required: true, message: '请选择状态', trigger: 'change' }]
  }
}

const rules = ref<FormRules>(getFormRules(false))

/** qms-defect 的 status 是缺陷处理状态，不是启用/禁用
 *  0=待处理 1=处理中 2=已闭环 */
function defectStatusLabel(code: string): string {
  const map: Record<string, string> = { '0': '待处理', '1': '处理中', '2': '已闭环' }
  return map[code] || code || '-'
}

function defectStatusTagType(code: string): string {
  const map: Record<string, string> = { '0': 'danger', '1': 'warning', '2': 'success' }
  return map[code] || 'info'
}

onMounted(() => {
  fetchList()
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getQmsDefectList({ page: query.page, size: query.size })
    total.value = res?.total ?? 0
    const allData: QmsDefect[] = Array.isArray(res) ? res : (res?.records || [])
    // 前端本地筛选（后端 list() 暂无筛选参数）
    const type = searchForm.defectType.trim().toLowerCase()
    const result = searchForm.handleResult.trim().toLowerCase()
    tableData.value = allData.filter(item => {
      const matchType = !type || (item.defectType || '').toLowerCase().includes(type)
      const matchResult = !result || (item.handleResult || '').toLowerCase().includes(result)
      return matchType && matchResult
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
  searchForm.defectType = ''
  searchForm.handleResult = ''
  fetchList()
}

function resetForm() {
  form.inspectionId = undefined
  form.workOrderId = undefined
  form.processId = undefined
  form.defectType = ''
  form.qty = 0
  form.handleResult = ''
  form.status = '0'
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  resetForm()
  rules.value = getFormRules(false)
  dialogVisible.value = true
}

function handleEdit(row: QmsDefect) {
  isEdit.value = true
  editId.value = row.defectId
  form.inspectionId = row.inspectionId
  form.workOrderId = row.workOrderId
  form.processId = row.processId
  form.defectType = row.defectType
  form.qty = row.qty
  form.handleResult = row.handleResult || ''
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
      await updateQmsDefect(editId.value, { ...form })
      ElMessage.success('编辑成功')
    } else {
      await createQmsDefect({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { submitting.value = false }
}

async function handleDelete(id: number) {
  try {
    await deleteQmsDefect(id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}
</script>

<style scoped>
/* 工具条仅放主操作：右对齐 */
.table-toolbar {
  justify-content: flex-end;
}
</style>
