<template>
  <PageContainer title="ERP 同步日志" description="查看 ERP 数据同步批次与结果。">
    <!-- 搜索栏（在当前已加载数据中筛选） -->
    <div class="search-bar">
      <el-input v-model="searchForm.bizType" placeholder="业务类型（本地筛选）" clearable />
      <el-input v-model="searchForm.syncStatus" placeholder="同步状态（本地筛选）" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-actions">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增同步日志
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="syncId" label="ID" width="80" class-name="mono-num" />
      <el-table-column prop="bizType" label="业务类型" width="120" />
      <el-table-column prop="bizId" label="业务 ID" width="120" />
      <el-table-column prop="syncDirection" label="同步方向" width="100">
        <template #default="{ row }">
          <el-tag :type="row.syncDirection === 'O' ? 'warning' : 'success'" size="small">
            {{ directionLabel(row.syncDirection) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="syncStatus" label="同步状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.syncStatus)" size="small">
            {{ statusLabel(row.syncStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="requestData" label="请求数据" min-width="180">
        <template #default="{ row }">
          <el-tooltip :content="row.requestData" placement="top" :show-after="500">
            <span class="text-ellipsis">{{ row.requestData || '-' }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="responseData" label="响应数据" min-width="180">
        <template #default="{ row }">
          <el-tooltip :content="row.responseData" placement="top" :show-after="500">
            <span class="text-ellipsis">{{ row.responseData || '-' }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="syncTime" label="同步时间" width="180" class-name="mono-num" />
      <el-table-column label="操作" min-width="150" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button
              type="warning"
              link
              size="small"
              title="重试同步"
              :disabled="row.syncStatus !== '3'"
              @click="handleRetry(row)"
            >
              重试
            </el-button>
            <el-button type="primary" link size="small" title="编辑同步日志" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该同步日志吗？" confirm-button-type="danger" @confirm="handleDelete(row.syncId)">
              <template #reference>
                <el-button type="danger" link size="small" title="删除同步日志">删除</el-button>
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
      :title="isEdit ? '编辑同步日志' : '新增同步日志'"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="业务类型" prop="bizType">
          <el-input v-model="form.bizType" placeholder="请输入业务类型，如 物料、工单" />
        </el-form-item>
        <el-form-item label="业务 ID" prop="bizId">
          <el-input v-model="form.bizId" placeholder="请输入业务 ID" />
        </el-form-item>
        <el-form-item label="同步方向" prop="syncDirection">
          <el-select v-model="form.syncDirection" placeholder="请选择同步方向" style="width: 100%">
            <el-option label="上行（MES → ERP）" value="O" />
            <el-option label="下行（ERP → MES）" value="I" />
          </el-select>
        </el-form-item>
        <el-form-item label="同步状态" prop="syncStatus">
          <el-select v-model="form.syncStatus" placeholder="请选择同步状态" style="width: 100%">
            <el-option label="待同步" value="0" />
            <el-option label="成功" value="2" />
            <el-option label="失败" value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="请求数据" prop="requestData">
          <el-input
            v-model="form.requestData"
            type="textarea"
            :rows="3"
            placeholder="请输入请求数据 JSON（选填）"
          />
        </el-form-item>
        <el-form-item label="响应数据" prop="responseData">
          <el-input
            v-model="form.responseData"
            type="textarea"
            :rows="3"
            placeholder="请输入响应数据 JSON（选填）"
          />
        </el-form-item>
        <el-form-item label="同步时间" prop="syncTime">
          <el-date-picker
            v-model="form.syncTime"
            type="datetime"
            placeholder="请选择同步时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
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
import { getErpSyncLogList, createErpSyncLog, updateErpSyncLog, deleteErpSyncLog, retryErpSyncLog } from '@/api/erpSyncLog'
import type { ErpSyncLog } from '@/types/api'

const DIRECTION_MAP: Record<string, string> = {
  O: '上行（MES → ERP）',
  I: '下行（ERP → MES）'
}
const STATUS_MAP: Record<string, string> = {
  '0': '待同步',
  '1': '同步中',
  '2': '成功',
  '3': '失败'
}
const directionLabel = (v?: string) => (v ? DIRECTION_MAP[v] ?? v : '-')
const statusLabel = (v?: string) => (v ? STATUS_MAP[v] ?? v : '-')
const statusTagType = (v?: string) => (v === '2' ? 'success' : v === '3' ? 'danger' : 'warning')

const tableData = ref<ErpSyncLog[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editId = ref<number>()

const searchForm = reactive({
  bizType: '',
  syncStatus: ''
})
const query = reactive({ page: 1, size: 15 })
const total = ref(0)

async function handleRetry(row: ErpSyncLog) {
  if (!row.syncId) return
  try {
    await retryErpSyncLog(row.syncId)
    ElMessage.success('重试成功')
    fetchList()
  } catch {
    /* 错误提示由请求拦截器统一处理 */
  }
}

const form = reactive<ErpSyncLog>({
  bizType: '',
  bizId: '',
  syncDirection: '',
  syncStatus: '',
  requestData: '',
  responseData: '',
  syncTime: ''
})

/** 新增和编辑校验规则分开定义，支持后续差异化 */
function getFormRules(_isEdit: boolean): FormRules {
  return {
    bizType: [{ required: true, message: '请输入业务类型', trigger: 'blur' }],
    bizId: [{ required: true, message: '请输入业务 ID', trigger: 'blur' }],
    syncDirection: [{ required: true, message: '请选择同步方向', trigger: 'change' }],
    syncStatus: [{ required: true, message: '请选择同步状态', trigger: 'change' }],
    // requestData: 新增和编辑都选填（DB TEXT NULL）
    // responseData: 新增和编辑都选填（DB TEXT NULL）
    syncTime: [{ required: true, message: '请选择同步时间', trigger: 'change' }]
  }
}

const rules = ref<FormRules>(getFormRules(false))

onMounted(() => {
  fetchList()
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getErpSyncLogList({ page: query.page, size: query.size })
    total.value = res?.total ?? 0
    const allData: ErpSyncLog[] = Array.isArray(res) ? res : (res?.records || [])
    const biz = searchForm.bizType.trim().toLowerCase()
    const st = searchForm.syncStatus.trim().toLowerCase()
    tableData.value = allData.filter(item => {
      const matchBiz = !biz || (item.bizType || '').toLowerCase().includes(biz)
      const matchSt = !st || (item.syncStatus || '').toLowerCase().includes(st)
      return matchBiz && matchSt
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
  searchForm.bizType = ''
  searchForm.syncStatus = ''
  fetchList()
}

function resetForm() {
  form.bizType = ''
  form.bizId = ''
  form.syncDirection = ''
  form.syncStatus = ''
  form.requestData = ''
  form.responseData = ''
  form.syncTime = ''
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  resetForm()
  rules.value = getFormRules(false)
  dialogVisible.value = true
}

function handleEdit(row: ErpSyncLog) {
  isEdit.value = true
  editId.value = row.syncId
  form.bizType = row.bizType
  form.bizId = row.bizId
  form.syncDirection = row.syncDirection
  form.syncStatus = row.syncStatus
  form.requestData = row.requestData || ''
  form.responseData = row.responseData || ''
  form.syncTime = row.syncTime || ''
  rules.value = getFormRules(true)
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateErpSyncLog(editId.value, { ...form })
      ElMessage.success('编辑成功')
    } else {
      await createErpSyncLog({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { submitting.value = false }
}

async function handleDelete(id: number) {
  try {
    await deleteErpSyncLog(id)
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
