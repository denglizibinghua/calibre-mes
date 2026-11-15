<template>
  <PageContainer title="安灯告警管理" description="处理现场安灯呼叫、升级与关闭。">
    <!-- 搜索栏（在当前已加载数据中筛选） -->
    <div class="search-bar">
      <el-input v-model="searchForm.alertNo" placeholder="告警编号（本地筛选）" clearable />
      <el-input v-model="searchForm.alertType" placeholder="告警类型（本地筛选）" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-actions">
        <el-button type="primary" v-permission="'admin:andon:manage'" @click="handleAdd">
          <el-icon><Plus /></el-icon> 上报告警
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column label="告警编号 / 类型" width="170">
        <template #default="{ row }">
          <div class="cell-stack">
            <strong class="mono-num">{{ row.alertNo }}</strong>
            <span class="cell-sub">{{ row.alertType || '-' }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="级别 / 状态" width="110">
        <template #default="{ row }">
          <div class="cell-tags">
            <el-tag :type="levelType(row.alertLevel)" size="small">{{ levelLabel(row.alertLevel) }}</el-tag>
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="关联对象" width="140">
        <template #default="{ row }">
          <span class="cell-rel">设备 <b>{{ row.equipmentId ?? '-' }}</b> · 单元 <b>{{ row.unitId ?? '-' }}</b> · 工单 <b>{{ row.workOrderId ?? '-' }}</b></span>
        </template>
      </el-table-column>
      <el-table-column label="人员" width="120">
        <template #default="{ row }">
          <div class="cell-stack">
            <span class="cell-sub">上报 {{ row.reporterUserId ?? '-' }}</span>
            <span class="cell-sub" :class="{ 'text-muted': !row.handlerUserId }">处理 {{ row.handlerUserId || '未受理' }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="上报时间" width="145" class-name="mono-num" />
      <el-table-column label="处理时间" width="145" class-name="mono-num">
        <template #default="{ row }">
          <span :class="{ 'text-muted': !row.handleTime }">{{ row.handleTime || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="升级状态" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.escalationStatus === '1'" type="danger" size="small">已升级</el-tag>
          <span v-else class="text-muted">未升级</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="180" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button
              v-if="row.status === '0'"
              type="primary" link size="small" title="受理告警" v-permission="'admin:andon:manage'"
              @click="handleAccept(row)"
            >受理</el-button>
            <el-button
              v-if="row.status === '1'"
              type="warning" link size="small" title="关闭告警" v-permission="'admin:andon:manage'"
              @click="handleClose(row)"
            >关闭</el-button>
            <el-button type="primary" link size="small" title="编辑告警" v-permission="'admin:andon:manage'" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button
              v-if="row.status !== '2' && row.escalationStatus !== '1'"
              type="danger" link size="small" title="升级告警" v-permission="'admin:andon:manage'"
              @click="handleEscalate(row)"
            >升级</el-button>
            <el-popconfirm title="确定删除该告警吗？" confirm-button-type="danger" @confirm="handleDelete(row.alertId)">
              <template #reference>
                <el-button type="danger" link size="small" title="删除告警" v-permission="'admin:andon:manage'">删除</el-button>
              </template>
            </el-popconfirm>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无数据" :image-size="72" />
      </template>
    </el-table>

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
      :title="isEdit ? '编辑告警' : '上报告警'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item v-if="!isEdit" label="告警编号" prop="alertNo">
          <el-input v-model="form.alertNo" placeholder="系统自动生成" disabled />
        </el-form-item>
        <el-form-item label="告警类型" prop="alertType">
          <el-select v-model="form.alertType" placeholder="请选择告警类型" style="width: 100%">
            <el-option v-for="t in alertTypeOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="告警等级" prop="alertLevel">
          <el-select v-model="form.alertLevel" placeholder="请选择告警等级" style="width: 100%">
            <el-option label="一般" value="1" />
            <el-option label="紧急" value="2" />
            <el-option label="严重" value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备 ID" prop="equipmentId">
          <el-input-number v-model="form.equipmentId" :min="1" placeholder="选填" style="width: 100%" />
        </el-form-item>
        <el-form-item label="生产单元 ID" prop="unitId">
          <el-input-number v-model="form.unitId" :min="1" placeholder="选填" style="width: 100%" />
        </el-form-item>
        <el-form-item label="工单 ID" prop="workOrderId">
          <el-input-number v-model="form.workOrderId" :min="1" placeholder="选填" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>

    <!-- 升级告警弹窗 -->
    <el-dialog v-model="escalateDialogVisible" title="升级告警" width="480px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="升级备注">
          <el-input
            v-model="escalateForm.remark"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="说明升级原因（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="escalateDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="escalating" @click="submitEscalate">确认升级</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, onUnmounted } from 'vue'
import { onWsConnected, useWebSocket } from '@/utils/useWebSocket'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import PageContainer from '@/components/PageContainer.vue'
import {
  getAndonAlertList,
  createAndonAlert,
  updateAndonAlert,
  acceptAndonAlert,
  closeAndonAlert,
  escalateAndonAlert,
  deleteAndonAlert,
} from '@/api/andonAlert'
import type { AndonAlert } from '@/types/api'

const alertTypeOptions = ['设备异常', '物料短缺', '质检异常', '工具故障', '安全事故', '人员缺勤', '工艺异常', '其他']

const tableData = ref<AndonAlert[]>([])
const loading = ref(false)
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editId = ref<number>()
let offWs: (() => void) | null = null
let offWsConnected: (() => void) | null = null
let listRequestSequence = 0
let listDisposed = false
const escalateDialogVisible = ref(false)
const escalating = ref(false)
const escalateForm = reactive({ remark: '' })
const escalateId = ref<number>()

const query = reactive({ page: 1, size: 15 })

const searchForm = reactive({
  alertNo: '',
  alertType: ''
})

const form = reactive<AndonAlert>({
  alertNo: '',
  alertType: '',
  alertLevel: '1',
  equipmentId: undefined,
  unitId: undefined,
  workOrderId: undefined,
  status: '0'
})

function getFormRules(isCreate: boolean): FormRules {
  return {
    alertNo: isCreate ? [{ required: true, message: '告警编号不能为空', trigger: 'blur' }] : [],
    alertType: [{ required: true, message: '请选择告警类型', trigger: 'change' }],
    alertLevel: [{ required: true, message: '请选择告警等级', trigger: 'change' }]
  }
}

const rules = ref<FormRules>(getFormRules(true))

onMounted(() => {
  fetchList()
  const ws = useWebSocket()
  ws.connect()
  offWs = ws.onWsMessage('ANDON', () => fetchList())
  offWsConnected = onWsConnected(() => fetchList())
})

onUnmounted(() => {
  listDisposed = true
  listRequestSequence += 1
  offWs?.()
  offWsConnected?.()
})

async function fetchList() {
  if (listDisposed) return
  const requestSequence = ++listRequestSequence
  loading.value = true
  try {
    const res = await getAndonAlertList({ page: query.page, size: query.size })
    if (listDisposed || requestSequence !== listRequestSequence) return
    const allData: AndonAlert[] = Array.isArray(res?.records) ? res.records : []
    total.value = res?.total ?? 0
    const no = searchForm.alertNo.trim().toLowerCase()
    const type = searchForm.alertType.trim().toLowerCase()
    tableData.value = allData.filter(item => {
      const matchNo = !no || (item.alertNo || '').toLowerCase().includes(no)
      const matchType = !type || (item.alertType || '').toLowerCase().includes(type)
      return matchNo && matchType
    })
  } catch { /* 已在 request 拦截器中处理 */ }
  finally {
    if (!listDisposed && requestSequence === listRequestSequence) loading.value = false
  }
}

function resetSearch() {
  searchForm.alertNo = ''
  searchForm.alertType = ''
  query.page = 1
  fetchList()
}

function handleSearch() {
  query.page = 1
  fetchList()
}

function genAlertNo(): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `ALERT${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
}

function resetForm() {
  form.alertNo = genAlertNo()
  form.alertType = ''
  form.alertLevel = '1'
  form.equipmentId = undefined
  form.unitId = undefined
  form.workOrderId = undefined
  form.status = '0'
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  resetForm()
  rules.value = getFormRules(true)
  dialogVisible.value = true
}

function handleEdit(row: AndonAlert) {
  isEdit.value = true
  editId.value = row.alertId
  form.alertNo = row.alertNo
  form.alertType = row.alertType
  form.alertLevel = row.alertLevel
  form.equipmentId = row.equipmentId
  form.unitId = row.unitId
  form.workOrderId = row.workOrderId
  form.status = row.status
  rules.value = getFormRules(false)
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateAndonAlert(editId.value, { ...form })
      ElMessage.success('编辑成功')
    } else {
      await createAndonAlert({ ...form })
      ElMessage.success('上报成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { submitting.value = false }
}

async function handleAccept(row: AndonAlert) {
  try {
    await ElMessageBox.confirm(`确认受理告警「${row.alertNo}」？受理后处理人为当前用户。`, '受理确认', { type: 'warning' })
  } catch { return }
  try {
    await acceptAndonAlert(row.alertId!)
    ElMessage.success('受理成功')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}

async function handleClose(row: AndonAlert) {
  try {
    await ElMessageBox.confirm(`确认关闭告警「${row.alertNo}」？关闭后不可恢复。`, '关闭确认', { type: 'warning' })
  } catch { return }
  try {
    await closeAndonAlert(row.alertId!)
    ElMessage.success('关闭成功')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}

function handleEscalate(row: AndonAlert) {
  escalateId.value = row.alertId
  escalateForm.remark = row.escalationRemark || ''
  escalateDialogVisible.value = true
}

async function submitEscalate() {
  if (!escalateId.value) return
  escalating.value = true
  try {
    await escalateAndonAlert(escalateId.value, { remark: escalateForm.remark.trim() || undefined })
    ElMessage.success('升级成功')
    escalateDialogVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { escalating.value = false }
}

async function handleDelete(id?: number) {
  if (!id) return
  try {
    await deleteAndonAlert(id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}

function levelLabel(level: string): string {
  const map: Record<string, string> = { '1': '一般', '2': '紧急', '3': '严重' }
  return map[level] ?? '未知'
}

function levelType(level: string): string {
  if (level === '1') return 'info'
  if (level === '2') return 'warning'
  return 'danger'
}

function statusLabel(status: string): string {
  const map: Record<string, string> = { '0': '待处理', '1': '处理中', '2': '已关闭' }
  return map[status] ?? '未知'
}

function statusType(status: string): string {
  if (status === '0') return 'warning'
  if (status === '1') return 'primary'
  return 'info'
}
</script>

<style scoped>
.cell-stack {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.cell-stack strong {
  overflow: hidden;
  color: var(--lux-text-strong);
  font-size: 12px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-sub {
  overflow: hidden;
  color: var(--lux-text-muted);
  font-size: 10.5px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.cell-rel {
  color: var(--lux-text-muted);
  font-size: 10.5px;
  line-height: 1.6;
}

.cell-rel b {
  color: var(--lux-text-strong);
  font-family: var(--lux-font-data);
  font-weight: 400;
}
</style>
