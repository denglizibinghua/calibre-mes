<template>
  <PageContainer title="设备维修工单" description="跟踪设备维修工单与维修结果。">
    <!-- 搜索栏（在当前已加载数据中筛选） -->
    <div class="search-bar">
      <el-input v-model="searchForm.faultDesc" placeholder="故障描述（本地筛选）" clearable />
      <el-input v-model="searchForm.reportUser" placeholder="报修人（本地筛选）" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-actions">
        <el-button type="primary" v-permission="'admin:repair:manage'" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增报修单
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="repairId" label="维修单 ID" width="100" class-name="mono-num" />
      <el-table-column prop="equipmentId" label="设备 ID" width="90" class-name="mono-num" />
      <el-table-column prop="faultDesc" label="故障描述" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ 'text-muted': !row.faultDesc }">{{ row.faultDesc || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="reportUser" label="报修人" width="110" show-overflow-tooltip />
      <el-table-column prop="reportTime" label="报修时间" width="170" class-name="mono-num" />
      <el-table-column label="维修人" width="110">
        <template #default="{ row }">
          <span :class="{ 'text-muted': !row.repairUser }">{{ row.repairUser || '未接单' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="resultDesc" label="维修结果" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ 'text-muted': !row.resultDesc }">
            {{ row.resultDesc || '未填写' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="完成时间" width="170" class-name="mono-num">
        <template #default="{ row }">
          <span :class="{ 'text-muted': !row.finishTime }">{{ row.finishTime || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="210" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button
              v-if="row.status === '0'"
              type="primary" link size="small" title="接单维修" v-permission="'admin:repair:manage'"
              @click="handleAccept(row)"
            >接单</el-button>
            <el-button
              v-if="row.status === '1'"
              type="success" link size="small" title="完成维修" v-permission="'admin:repair:manage'"
              @click="openCompleteDialog(row)"
            >完成维修</el-button>
            <el-button type="primary" link size="small" title="编辑维修单" v-permission="'admin:repair:manage'" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-popconfirm title="确定删除该维修单吗？" confirm-button-type="danger" @confirm="handleDelete(row.repairId)">
              <template #reference>
                <el-button type="danger" link size="small" title="删除维修单" v-permission="'admin:repair:manage'">删除</el-button>
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
      :title="isEdit ? '编辑维修单' : '新增报修单'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="设备 ID" prop="equipmentId">
          <el-input-number v-model="form.equipmentId" :min="1" placeholder="请输入设备 ID" style="width: 100%" />
        </el-form-item>
        <el-form-item label="故障描述" prop="faultDesc">
          <el-input v-model="form.faultDesc" type="textarea" :rows="4" placeholder="请描述设备故障情况" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>

    <!-- 完成维修对话框 -->
    <el-dialog v-model="completeVisible" title="完成维修" width="520px" :close-on-click-modal="false">
      <el-form ref="completeFormRef" :model="completeForm" :rules="completeRules" label-width="100px">
        <el-form-item label="维修结果" prop="resultDesc">
          <el-input
            v-model="completeForm.resultDesc"
            type="textarea"
            :rows="4"
            placeholder="请填写维修结果/处理说明（必填）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCompleteSubmit" :loading="submitting">确认完成</el-button>
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
  getEqmRepairOrderList,
  createEqmRepairOrder,
  updateEqmRepairOrder,
  acceptEqmRepairOrder,
  completeEqmRepairOrder,
  deleteEqmRepairOrder,
} from '@/api/eqmRepairOrder'
import type { EqmRepairOrder } from '@/types/api'

const tableData = ref<EqmRepairOrder[]>([])
const loading = ref(false)
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editId = ref<number>()

const completeVisible = ref(false)
const completeFormRef = ref<FormInstance>()
const completingId = ref<number>()
const completeForm = reactive({ resultDesc: '' })

let offWs: (() => void) | null = null
let offWsConnected: (() => void) | null = null
let listRequestSequence = 0
let listDisposed = false
const query = reactive({ page: 1, size: 15 })

const searchForm = reactive({
  faultDesc: '',
  reportUser: ''
})

const form = reactive<EqmRepairOrder>({
  equipmentId: undefined,
  faultDesc: '',
  reportUser: '',
  reportTime: '',
  repairUser: '',
  finishTime: '',
  status: '0',
  resultDesc: ''
})

function getFormRules(): FormRules {
  return {
    equipmentId: [{ required: true, message: '请输入设备 ID', trigger: 'change' }],
    faultDesc: [{ required: true, message: '请输入故障描述', trigger: 'blur' }]
  }
}

const rules = ref<FormRules>(getFormRules())

const completeRules: FormRules = {
  resultDesc: [{ required: true, message: '请输入维修结果', trigger: 'blur' }]
}

onMounted(() => {
  fetchList()
  const ws = useWebSocket()
  ws.connect()
  offWs = ws.onWsMessage('REPAIR', () => fetchList())
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
    const res = await getEqmRepairOrderList({ page: query.page, size: query.size })
    if (listDisposed || requestSequence !== listRequestSequence) return
    const allData: EqmRepairOrder[] = Array.isArray(res?.records) ? res.records : []
    total.value = res?.total ?? 0
    const desc = searchForm.faultDesc.trim().toLowerCase()
    const user = searchForm.reportUser.trim().toLowerCase()
    tableData.value = allData.filter(item => {
      const matchDesc = !desc || (item.faultDesc || '').toLowerCase().includes(desc)
      const matchUser = !user || (item.reportUser || '').toLowerCase().includes(user)
      return matchDesc && matchUser
    })
  } catch { /* 已在 request 拦截器中处理 */ }
  finally {
    if (!listDisposed && requestSequence === listRequestSequence) loading.value = false
  }
}

function resetSearch() {
  searchForm.faultDesc = ''
  searchForm.reportUser = ''
  query.page = 1
  fetchList()
}

function handleSearch() {
  query.page = 1
  fetchList()
}

function resetForm() {
  form.equipmentId = undefined
  form.faultDesc = ''
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: EqmRepairOrder) {
  isEdit.value = true
  editId.value = row.repairId
  form.equipmentId = row.equipmentId
  form.faultDesc = row.faultDesc
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateEqmRepairOrder(editId.value, {
        equipmentId: form.equipmentId,
        faultDesc: form.faultDesc,
      })
      ElMessage.success('编辑成功')
    } else {
      await createEqmRepairOrder({
        equipmentId: form.equipmentId,
        faultDesc: form.faultDesc,
      })
      ElMessage.success('报修成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { submitting.value = false }
}

async function handleAccept(row: EqmRepairOrder) {
  try {
    await ElMessageBox.confirm(`确认接单维修单「#${row.repairId}」？接单后维修人为当前用户。`, '接单确认', { type: 'warning' })
  } catch { return }
  try {
    await acceptEqmRepairOrder(row.repairId!)
    ElMessage.success('接单成功')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}

function openCompleteDialog(row: EqmRepairOrder) {
  completingId.value = row.repairId
  completeForm.resultDesc = ''
  completeVisible.value = true
}

async function handleCompleteSubmit() {
  const valid = await completeFormRef.value?.validate().catch(() => false)
  if (!valid || !completingId.value) return

  submitting.value = true
  try {
    await completeEqmRepairOrder(completingId.value, { resultDesc: completeForm.resultDesc })
    ElMessage.success('维修完成')
    completeVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { submitting.value = false }
}

async function handleDelete(id?: number) {
  if (!id) return
  try {
    await deleteEqmRepairOrder(id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}

function statusLabel(status: string): string {
  const map: Record<string, string> = { '0': '待受理', '1': '维修中', '2': '已完成' }
  return map[status] ?? '未知'
}

function statusType(status: string): string {
  if (status === '0') return 'warning'
  if (status === '1') return 'primary'
  return 'success'
}
</script>

<style scoped>
/* 工具条仅承载主操作：右对齐；窄屏由全局工具条规则自动换行 */
.table-toolbar {
  justify-content: flex-end;
}
</style>
