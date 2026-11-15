<template>
  <PageContainer title="工单管理" description="创建、下达并跟踪生产工单进度。">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="searchForm.workOrderNo" placeholder="工单号" clearable />
      <el-input v-model="searchForm.orderNo" placeholder="订单号" clearable />
      <el-input v-model="searchForm.productName" placeholder="产品名称" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-actions">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增工单
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="workOrderId" label="ID" width="80" class-name="mono-num" />
      <el-table-column prop="workOrderNo" label="工单号" width="180" show-overflow-tooltip />
      <el-table-column prop="orderNo" label="订单号" width="180" show-overflow-tooltip />
      <el-table-column label="产品" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="itemMap.has(row.itemId)">
            {{ itemMap.get(row.itemId)?.itemCode }} - {{ itemMap.get(row.itemId)?.itemName }}
          </template>
          <template v-else>ID: {{ row.itemId }}</template>
        </template>
      </el-table-column>
      <el-table-column prop="planQty" label="计划数量" width="100" class-name="mono-num" />
      <el-table-column prop="unit" label="单位" width="80" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag
            :type="statusTagType(row.status)"
            size="small"
          >{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="assigneeUserId" label="执行人" width="120">
        <template #default="{ row }">
          <span v-if="row.assigneeUserId">{{ getAssigneeName(row.assigneeUserId) }}</span>
          <span v-else class="text-muted">未指派</span>
        </template>
      </el-table-column>
      <el-table-column prop="planStartTime" label="计划开始" width="180" class-name="mono-num" />
      <el-table-column prop="planEndTime" label="计划结束" width="180" class-name="mono-num" />
      <el-table-column prop="createTime" label="创建时间" width="180" class-name="mono-num" />
      <el-table-column label="操作" min-width="330" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button v-permission="'admin:work-order:view'" type="primary" link size="small" title="查看工单二维码" @click="handleQrCode(row.workOrderId)">工单二维码</el-button>
            <!-- 状态依赖操作按钮 -->
            <el-button v-if="row.status === '0'" type="success" link size="small" title="下达工单" @click="handleRelease(row.workOrderId!)">下达</el-button>
            <el-button v-if="row.status === '1'" type="warning" link size="small" title="暂停工单" @click="handlePause(row.workOrderId!)">暂停</el-button>
            <el-button v-if="row.status === '5'" type="primary" link size="small" title="恢复工单" @click="handleResume(row.workOrderId!)">恢复</el-button>
            <el-button v-if="row.status === '2'" type="success" link size="small" title="关闭工单" @click="handleClose(row.workOrderId!)">关闭</el-button>
            <el-button v-if="row.status === '0' || row.status === '4'" type="danger" link size="small" title="取消工单" @click="handleCancel(row.workOrderId!)">取消</el-button>
            <!-- 指派操作 -->
            <el-button v-if="!row.assigneeUserId && (row.status === '0' || row.status === '4')" type="warning" link size="small" title="指派执行人" @click="handleAssign(row)">指派</el-button>
            <el-button v-if="row.assigneeUserId && (row.status === '0' || row.status === '4' || row.status === '1')" type="primary" link size="small" title="更换执行人" @click="handleReassign(row)">更换</el-button>
            <el-button v-if="row.assigneeUserId && (row.status === '0' || row.status === '4')" type="info" link size="small" title="取消指派" @click="handleUnassign(row.workOrderId!)">取消指派</el-button>
            <!-- 基础操作（非终态） -->
            <el-button v-if="row.status !== '3' && row.status !== '6'" type="primary" link size="small" title="编辑工单" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm v-if="row.status !== '3' && row.status !== '6'" title="确定删除该工单吗？" confirm-button-type="danger" @confirm="handleDelete(row.workOrderId!)">
              <template #reference>
                <el-button type="danger" link size="small" title="删除工单">删除</el-button>
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

    <WorkOrderQrDialog v-model="qrDialogVisible" :work-order-id="qrWorkOrderId" />

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑工单' : '新增工单'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="工单号" prop="workOrderNo">
          <el-input v-model="form.workOrderNo" placeholder="请输入工单号" />
        </el-form-item>
        <el-form-item label="订单号" prop="orderNo">
          <el-input v-model="form.orderNo" placeholder="请输入订单号" />
        </el-form-item>
        <el-form-item label="产品类型">
          <el-select v-model="filterItemType" placeholder="全部类型（可选筛选）" clearable style="width: 100%">
            <el-option label="成品" value="P" />
            <el-option label="半成品" value="S" />
            <el-option label="零部件" value="M" />
            <el-option label="原材料" value="R" />
          </el-select>
        </el-form-item>
        <el-form-item label="产品" prop="itemId">
          <el-select
            v-model="form.itemId"
            placeholder="请选择产品"
            filterable
            clearable
            style="width: 100%"
            @change="onItemChange"
          >
            <el-option
              v-for="item in filteredItems"
              :key="item.itemId"
              :label="`${item.itemCode} - ${item.itemName}`"
              :value="item.itemId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="计划数量" prop="planQty">
          <el-input-number v-model="form.planQty" :min="1" placeholder="请输入计划数量" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-input v-model="form.unit" placeholder="请输入单位" />
        </el-form-item>
        <el-form-item label="派工单元" prop="workUnitId">
          <el-select
            v-model="form.workUnitId"
            placeholder="请选择派工单元"
            filterable
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="u in workUnitOptions"
              :key="u.unitId"
              :label="`${u.unitCode} - ${u.unitName}`"
              :value="u.unitId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="工艺路线" prop="routeId">
          <el-select
            v-model="form.routeId"
            placeholder="请选择工艺路线"
            filterable
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="r in routeOptions"
              :key="r.routeId"
              :label="`${r.routeCode} (${r.version})`"
              :value="r.routeId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="计划开始" prop="planStartTime">
          <el-date-picker
            v-model="form.planStartTime"
            type="datetime"
            placeholder="请选择计划开始时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="计划结束" prop="planEndTime">
          <el-date-picker
            v-model="form.planEndTime"
            type="datetime"
            placeholder="请选择计划结束时间"
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

    <!-- 指派执行人对话框 -->
    <el-dialog
      v-model="assignDialogVisible"
      :title="assignMode === 'assign' ? '指派执行人' : '更换执行人'"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form label-width="80px">
        <el-form-item label="选择操作员">
          <el-select v-model="selectedAssigneeId" placeholder="请选择操作员" filterable style="width: 100%">
            <el-option
              v-for="user in activeUsers"
              :key="user.userId"
              :label="`${user.nickname || user.username} (${user.username})`"
              :value="user.userId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAssignSubmit" :loading="assigning">确定</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { reactive, ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { onWsConnected, useWebSocket } from '@/utils/useWebSocket'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import PageContainer from '@/components/PageContainer.vue'
import WorkOrderQrDialog from '@/components/WorkOrderQrDialog.vue'
import {
  getProWorkOrderList, createProWorkOrder, updateProWorkOrder, deleteProWorkOrder,
  releaseWorkOrder, pauseWorkOrder, resumeWorkOrder, closeWorkOrder, cancelWorkOrder,
  getActiveUsers, assignAssignee, reassignAssignee, unassignAssignee
} from '@/api/proWorkOrder'
import { idempotencyKey } from '@/utils/idempotencyKey'
import type { ProWorkOrder, BaseItem, BaseWorkUnit, BaseRoute } from '@/types/api'
import { WORK_ORDER_STATUS_MAP, getStatusLabel as getWoStatusLabel, getStatusTagType as getWoStatusTagType } from '@/constants/statusMap'
import { getBaseItemList } from '@/api/baseItem'
import { getBaseWorkUnitList } from '@/api/baseWorkUnit'
import { getBaseRouteList } from '@/api/baseRoute'

interface SimpleUser {
  userId: number
  username: string
  nickname: string
  roleKey: string
}

const tableData = ref<ProWorkOrder[]>([])
const loading = ref(false)
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editId = ref<number>()
const qrDialogVisible = ref(false)
const qrWorkOrderId = ref<number>()

function handleQrCode(id: number | undefined) {
  qrWorkOrderId.value = id
  qrDialogVisible.value = true
}

// 指派相关
const assignDialogVisible = ref(false)
const assignMode = ref<'assign' | 'reassign'>('assign')
const assignWorkOrderId = ref<number>(0)
const selectedAssigneeId = ref<number>()
const assigning = ref(false)
const activeUsers = ref<SimpleUser[]>([])

/** 所有启用状态的产品列表（用于联动下拉） */
const allItems = ref<BaseItem[]>([])

/** 产品类型筛选（非必选，仅用于前端过滤下拉列表） */
const filterItemType = ref('')

/** 根据产品类型筛选后的产品列表 */
const filteredItems = computed(() => {
  if (!filterItemType.value) return allItems.value
  return allItems.value.filter(item => item.itemType === filterItemType.value)
})

/** 产品 ID → 产品信息映射（表格显示用） */
const itemMap = ref<Map<number, BaseItem>>(new Map())

// 产品类型筛选变化时，若当前选中产品不在新筛选列表中则清空
watch(filterItemType, () => {
  if (form.itemId != null) {
    const stillExists = filteredItems.value.some(item => item.itemId === form.itemId)
    if (!stillExists) form.itemId = undefined
  }
})

/** 可选派工单元列表（仅启用状态） */
const workUnitOptions = ref<BaseWorkUnit[]>([])
/** 派工单元 ID → 信息映射（表格显示用） */
const workUnitMap = ref<Map<number, BaseWorkUnit>>(new Map())

/** 可选工艺路线列表（仅启用状态） */
const routeOptions = ref<BaseRoute[]>([])
/** 工艺路线 ID → 信息映射（表格显示用） */
const routeMap = ref<Map<number, BaseRoute>>(new Map())

// [前端调用] 分页参数：page 和 size 通过 v-model 双向绑定到 el-pagination 组件
let offWs: (() => void) | null = null
let offWsConnected: (() => void) | null = null
let listRequestSequence = 0
let listDisposed = false
const query = reactive({
  page: 1,
  size: 15
})

// [前端调用] 搜索表单数据，绑定到搜索栏的 el-input
const searchForm = reactive({
  workOrderNo: '',
  orderNo: '',
  productName: ''
})

const form = reactive<ProWorkOrder>({
  workOrderNo: '',
  orderNo: '',
  itemId: undefined,
  planQty: 1,
  unit: '',
  planStartTime: '',
  planEndTime: '',
  workUnitId: undefined,
  routeId: undefined,
  status: '0'
})

const rules: FormRules = {
  workOrderNo: [{ required: true, message: '请输入工单号', trigger: 'blur' }],
  itemId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  planQty: [{ required: true, message: '请输入计划数量', trigger: 'change' }]
}

// [前端调用] V3.4: 使用集中状态映射（WORK_ORDER_STATUS_MAP 定义在 constants/statusMap.ts）
const statusTagType = (status: string) => getWoStatusTagType(WORK_ORDER_STATUS_MAP, status)
const statusLabel = (status: string) => getWoStatusLabel(WORK_ORDER_STATUS_MAP, status)

onMounted(() => {
  fetchList()  // 页面挂载时自动加载第一页数据
  const ws = useWebSocket()
  ws.connect()
  offWs = ws.onWsMessage('WORK_ORDER', () => fetchList())
  offWsConnected = onWsConnected(() => fetchList())
  fetchActiveUsers()
  fetchAllItems()
  fetchWorkUnitOptions()
  fetchRouteOptions()
})

onUnmounted(() => {
  listDisposed = true
  listRequestSequence += 1
  offWs?.()
  offWsConnected?.()
})

async function fetchActiveUsers() {
  try {
    const res = await getActiveUsers()
    activeUsers.value = Array.isArray(res) ? res : (res as any)?.data ?? []
  } catch { /* ignore */ }
}

async function fetchAllItems() {
  try {
    const res = await getBaseItemList({ page: 1, size: 9999 })
    const all: BaseItem[] = Array.isArray(res?.records) ? res.records : []
    allItems.value = all.filter(item => item.status === '0')
    itemMap.value = new Map(all.map(item => [item.itemId!, item]))
  } catch { /* 已在拦截器处理 */ }
}

async function fetchWorkUnitOptions() {
  try {
    const res = await getBaseWorkUnitList({ page: 1, size: 9999 })
    const all: BaseWorkUnit[] = Array.isArray(res?.records) ? res.records : []
    workUnitOptions.value = all.filter(u => u.status === '0')
    workUnitMap.value = new Map(all.map(u => [u.unitId!, u]))
  } catch { /* ignore */ }
}

async function fetchRouteOptions() {
  try {
    const res = await getBaseRouteList({ page: 1, size: 9999 })
    const all: BaseRoute[] = Array.isArray(res?.records) ? res.records : []
    routeOptions.value = all.filter(r => r.status === '0')
    routeMap.value = new Map(all.map(r => [r.routeId!, r]))
  } catch { /* ignore */ }
}

/** 选择产品后，从全量映射反查类型并回填类型筛选 */
function onItemChange(itemId: number | undefined) {
  if (itemId != null) {
    const matched = itemMap.value.get(itemId)
    if (matched) filterItemType.value = matched.itemType
  }
}

function getAssigneeName(userId: number): string {
  const user = activeUsers.value.find(u => u.userId === userId)
  return user ? (user.nickname || user.username) : `用户#${userId}`
}

/**
 * [前端调用] 分页查询工单列表。
 * 调用链：fetchList → getProWorkOrderList → GET /admin/pro-work-order/list → ProWorkOrderController.list
 * [数据转换] 后端返回 PageResult.records 存入 tableData（el-table 的 :data 绑定），total 存入分页组件
 */
async function fetchList() {
  if (listDisposed) return
  const requestSequence = ++listRequestSequence
  loading.value = true
  try {
    const res = await getProWorkOrderList({ page: query.page, size: query.size, ...searchForm })
    if (listDisposed || requestSequence !== listRequestSequence) return
    // res.records：后端返回的当前页数据数组
    tableData.value = Array.isArray(res?.records) ? res.records : []
    total.value = res?.total ?? 0
  } catch { /* 已在 request 拦截器中处理 */ }
  finally {
    if (!listDisposed && requestSequence === listRequestSequence) loading.value = false
  }
}

// [前端调用] 搜索：重置搜索条件并回到第一页
function resetSearch() {
  searchForm.workOrderNo = ''
  searchForm.orderNo = ''
  searchForm.productName = ''
  query.page = 1
  fetchList()
}

// [前端调用] 查询：页码重置为 1 后重新请求（因为搜索条件变了）
function handleSearch() {
  query.page = 1
  fetchList()
}

function resetForm() {
  form.workOrderNo = ''
  form.orderNo = ''
  form.itemId = undefined
  form.planQty = 1
  form.unit = ''
  form.planStartTime = ''
  form.planEndTime = ''
  form.workUnitId = undefined
  form.routeId = undefined
  filterItemType.value = ''
}

//点击【新增】按钮，打开空白新增弹窗
function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  resetForm()
  dialogVisible.value = true
  fetchAllItems()
  fetchWorkUnitOptions()
  fetchRouteOptions()
}

function handleEdit(row: ProWorkOrder) {
  isEdit.value = true
  editId.value = row.workOrderId
  form.workOrderNo = row.workOrderNo
  form.orderNo = row.orderNo
  form.itemId = row.itemId
  form.planQty = row.planQty
  form.unit = row.unit
  form.planStartTime = row.planStartTime || ''
  form.planEndTime = row.planEndTime || ''
  form.workUnitId = row.workUnitId
  form.routeId = row.routeId
  dialogVisible.value = true
  fetchAllItems().then(() => {
    // 从全量映射中查找（含禁用产品），自动回填产品类型筛选
    const matched = itemMap.value.get(row.itemId!)
    if (matched) filterItemType.value = matched.itemType
  })
  fetchWorkUnitOptions()
  fetchRouteOptions()
}

//新增函数:标记为清空模式，清空表单，打开弹窗
async function handleSubmit() {
  //校验是否填完
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateProWorkOrder(editId.value, { ...form })
      ElMessage.success('编辑成功')
    } else {
      //传给后端
      await createProWorkOrder({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { submitting.value = false }
}

async function handleDelete(id: number) {
  try {
    await deleteProWorkOrder(id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}

// ==================== V3.4 状态转换操作 ====================
// [状态变化] 每个按钮根据工单当前状态显示（v-if 条件），调用对应的后端状态转换接口
// 状态对应关系：0=已创建, 1=生产中, 2=已完工, 3=已关闭, 4=已下达, 5=已暂停, 6=已取消

// [状态变化] 已创建(0) → 已下达(4)
async function handleRelease(id: number) {
  const idmActionKey = `release-wo-${id}`
  try {
    const key = idempotencyKey.getExisting(idmActionKey) ?? idempotencyKey.getOrCreate(idmActionKey)
    await releaseWorkOrder(id, key)
    idempotencyKey.clear(idmActionKey)
    ElMessage.success('工单已下达')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}

// [状态变化] 生产中(1) → 已暂停(5)
async function handlePause(id: number) {
  try {
    await pauseWorkOrder(id)
    ElMessage.success('工单已暂停')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}

// [状态变化] 已暂停(5) → 生产中(1)
async function handleResume(id: number) {
  try {
    await resumeWorkOrder(id)
    ElMessage.success('工单已恢复')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}

// [状态变化] 已完工(2) → 已关闭(3)
async function handleClose(id: number) {
  try {
    await closeWorkOrder(id)
    ElMessage.success('工单已关闭')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}

// ==================== 执行人指派 ====================

function handleAssign(row: ProWorkOrder) {
  assignMode.value = 'assign'
  assignWorkOrderId.value = row.workOrderId!
  selectedAssigneeId.value = undefined
  assignDialogVisible.value = true
}

function handleReassign(row: ProWorkOrder) {
  assignMode.value = 'reassign'
  assignWorkOrderId.value = row.workOrderId!
  selectedAssigneeId.value = undefined
  assignDialogVisible.value = true
}

async function handleAssignSubmit() {
  if (!selectedAssigneeId.value) {
    ElMessage.warning('请选择操作员')
    return
  }
  assigning.value = true
  try {
    if (assignMode.value === 'assign') {
      await assignAssignee(assignWorkOrderId.value, selectedAssigneeId.value)
      ElMessage.success('指派成功')
    } else {
      await reassignAssignee(assignWorkOrderId.value, selectedAssigneeId.value)
      ElMessage.success('更换成功')
    }
    assignDialogVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { assigning.value = false }
}

async function handleUnassign(id: number) {
  try {
    await unassignAssignee(id)
    ElMessage.success('已取消指派')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}

// [状态变化] 已创建(0) 或 已下达(4) → 已取消(6)
async function handleCancel(id: number) {
  try {
    await cancelWorkOrder(id)
    ElMessage.success('工单已取消')
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
