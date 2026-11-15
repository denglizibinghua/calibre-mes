<template>
  <PageContainer title="生产工序" description="跟踪工单工序报工与状态流转。">
    <!-- 新增工序已禁用：工序由工单下达自动生成，不支持手工创建 -->

    <!-- 搜索栏（在当前已加载数据中筛选） -->
    <div class="search-bar">
      <el-input v-model="searchForm.operator" placeholder="操作员（本地筛选）" clearable />
      <el-input v-model="searchForm.qcResult" placeholder="质检结果（本地筛选）" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="processRecordId" label="ID" width="80" class-name="mono-num" />
      <el-table-column prop="workOrderId" label="工单 ID" width="100" class-name="mono-num" />
      <el-table-column prop="processId" label="工序 ID" width="100" class-name="mono-num" />
      <el-table-column prop="equipmentId" label="设备 ID" width="100" class-name="mono-num">
        <template #default="{ row }">
          <span :class="{ 'text-muted': !row.equipmentId }">
            {{ row.equipmentId ?? '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="operator" label="操作员" width="120" show-overflow-tooltip />
      <el-table-column prop="startTime" label="开始时间" width="180" class-name="mono-num" />
      <el-table-column prop="endTime" label="结束时间" width="180" class-name="mono-num">
        <template #default="{ row }">
          <span :class="{ 'text-muted': !row.endTime }">
            {{ row.endTime || '进行中' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="reportQty" label="报工数量" width="100" class-name="mono-num" />
      <el-table-column prop="qcResult" label="质检结果" width="120">
        <template #default="{ row }">
          <el-tag
            v-if="row.qcResult"
            :type="row.qcResult === '1' ? 'success' : row.qcResult === '2' ? 'danger' : 'warning'"
            size="small"
          >
            {{ qcResultLabel(row.qcResult) }}
          </el-tag>
          <span v-else class="text-muted">待检</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="processStatusTagType(row.status)" size="small">
            {{ processStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" class-name="mono-num" />
      <el-table-column label="操作" min-width="250" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <!-- qc-pass/qc-fail 已废弃：所有质检判定必须通过质检单确认流程 -->
            <el-button v-if="row.status === '2'" type="warning" link size="small" title="重新发起工序" @click="handleSkipProcess(row.processRecordId)">重新发起</el-button>
            <el-button v-if="row.status === '3'" type="info" link size="small" title="查看关联质检单" @click="handleViewInspection(row)">查看质检单</el-button>
            <el-button v-if="row.status === '5'" type="warning" link size="small" title="发起返工" @click="handleReworkStart(row.processRecordId)">发起返工</el-button>
            <el-button v-if="row.status === '6'" type="primary" link size="small" title="确认返工完成" @click="handleReworkComplete(row.processRecordId)">返工完成</el-button>
            <el-button v-if="row.status === '0' || row.status === '9'" type="warning" link size="small" title="跳过工序" @click="handleSkipProcess(row.processRecordId)">跳过</el-button>
            <el-button v-if="row.status === '0'" type="danger" link size="small" title="取消工序" @click="handleCancelProcess(row.processRecordId)">取消</el-button>
            <!-- 编辑（仅允许修改设备绑定） -->
            <el-button v-if="row.status !== '2' && row.status !== '7' && row.status !== '8'" type="primary" link size="small" title="编辑设备绑定" @click="handleEdit(row)">编辑</el-button>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无数据" :image-size="72" />
      </template>
    </el-table>

    <!-- 分页 -->
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

    <!-- 编辑对话框（仅允许修改设备绑定） -->
    <el-dialog
      v-model="dialogVisible"
      title="编辑设备绑定"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-alert
        title="仅允许修改设备绑定。工单/工序/状态/报工数量/操作员等字段不可通过此页面修改。"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-form ref="formRef" :model="form" label-width="100px">
        <el-form-item label="设备 ID">
          <el-input-number
            v-model="form.equipmentId"
            :min="0"
            placeholder="请输入设备 ID（留空解除绑定）"
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
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import PageContainer from '@/components/PageContainer.vue'
import { useRouter } from 'vue-router'
import {
  getProWorkProcessList, updateProWorkProcess,
  reworkStartWorkProcess, reworkCompleteWorkProcess,
  skipWorkProcess, cancelWorkProcess
} from '@/api/proWorkProcess'
import { getQmsInspectionList } from '@/api/qmsInspection'
import { idempotencyKey } from '@/utils/idempotencyKey'
import type { ProWorkProcess } from '@/types/api'
import {
  WORK_PROCESS_STATUS_MAP, QC_RESULT_MAP,
  getStatusLabel, getStatusTagType
} from '@/constants/statusMap'

const tableData = ref<ProWorkProcess[]>([])
const loading = ref(false)
const total = ref(0)
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editId = ref<number>()
const router = useRouter()

const query = reactive({
  page: 1,
  size: 15
})

const searchForm = reactive({
  operator: '',
  qcResult: ''
})

const form = reactive({
  equipmentId: undefined as number | undefined,
  version: undefined as number | undefined
})

onMounted(() => {
  fetchList()
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getProWorkProcessList({ page: query.page, size: query.size })
    const allData: ProWorkProcess[] = Array.isArray(res?.records) ? res.records : []
    total.value = res?.total ?? 0
    const op = searchForm.operator.trim().toLowerCase()
    const qc = searchForm.qcResult.trim().toLowerCase()
    tableData.value = allData.filter(item => {
      const matchOp = !op || (item.operator || '').toLowerCase().includes(op)
      const matchQc = !qc || (item.qcResult || '').toLowerCase().includes(qc)
      return matchOp && matchQc
    })
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { loading.value = false }
}

function resetSearch() {
  searchForm.operator = ''
  searchForm.qcResult = ''
  query.page = 1
  fetchList()
}

function handleSearch() {
  query.page = 1
  fetchList()
}

const qcResultLabel = (code: string) => getStatusLabel(QC_RESULT_MAP, code)
const processStatusLabel = (status: string) => getStatusLabel(WORK_PROCESS_STATUS_MAP, status)
const processStatusTagType = (status: string) => getStatusTagType(WORK_PROCESS_STATUS_MAP, status)

// ==================== 编辑 ====================

function handleEdit(row: ProWorkProcess) {
  editId.value = row.processRecordId
  form.equipmentId = row.equipmentId ?? undefined
  form.version = row.version
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!editId.value) return
  submitting.value = true
  try {
    await updateProWorkProcess(editId.value, {
      equipmentId: form.equipmentId ?? undefined,
      version: form.version
    })
    ElMessage.success('编辑成功')
    dialogVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { submitting.value = false }
}

// ==================== 返工 ====================

async function handleViewInspection(row: ProWorkProcess) {
  if (!row.processRecordId) return
  try {
    const res = await getQmsInspectionList({ page: 1, size: 10, processRecordId: row.processRecordId })
    const records = res?.records ?? []
    if (records.length === 0) {
      ElMessage.warning('该工序暂无关联质检单')
      return
    }
    const latest = records[0]
    await router.push({ path: '/quality/inspection', query: { inspectionNo: latest.inspectionNo } })
  } catch {
    ElMessage.warning('查询质检单失败，请前往质检单总览查看')
  }
}

async function handleReworkStart(id: number) {
  const { value: reason } = await ElMessageBox.prompt('请输入强制返工原因', '管理端强制返工', {
    confirmButtonText: '确认发起',
    cancelButtonText: '取消',
    inputPattern: /.+/,
    inputErrorMessage: '返工原因不能为空',
  }).catch(() => ({ value: null }))
  if (!reason) return

  const idmActionKey = `rework-start-${id}`
  try {
    const key = idempotencyKey.getExisting(idmActionKey) ?? idempotencyKey.getOrCreate(idmActionKey)
    await reworkStartWorkProcess(id, reason, key)
    idempotencyKey.clear(idmActionKey)
    ElMessage.success('已发起强制返工')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}

async function handleReworkComplete(id: number) {
  const idmActionKey = `rework-complete-${id}`
  try {
    const key = idempotencyKey.getExisting(idmActionKey) ?? idempotencyKey.getOrCreate(idmActionKey)
    await reworkCompleteWorkProcess(id, undefined, key)
    idempotencyKey.clear(idmActionKey)
    ElMessage.success('返工已完成，待复检')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}

// ==================== 跳过 / 取消 ====================

async function handleSkipProcess(id: number) {
  try {
    await skipWorkProcess(id)
    ElMessage.success('工序已跳过')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}

async function handleCancelProcess(id: number) {
  try {
    await cancelWorkProcess(id)
    ElMessage.success('工序已取消')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}
</script>
