<template>
  <PageContainer title="库存台账" description="查询实时库存、库位分布与预警。">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="searchForm.itemCode" placeholder="物料编码" clearable />
      <el-input v-model="searchForm.itemName" placeholder="物料名称" clearable />
      <el-input v-model="searchForm.warehouseName" placeholder="库位名称" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-actions">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增库存记录
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="inventoryId" label="ID" width="60" class-name="mono-num" />
      <el-table-column label="物料" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.itemCode">{{ row.itemCode }}</span>
          <span v-else class="text-muted">ID:{{ row.itemId }}</span>
          <span v-if="row.itemName" class="cell-sub">{{ row.itemName }}</span>
        </template>
      </el-table-column>
      <el-table-column label="库位" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.locationCode">{{ row.locationCode }}</span>
          <span v-else class="text-muted">ID:{{ row.locationId }}</span>
          <span v-if="row.locationName" class="cell-sub">{{ row.locationName }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="qty" label="库存数量" width="90" class-name="mono-num" />
      <el-table-column prop="lockedQty" label="锁定数量" width="80" class-name="mono-num" />
      <el-table-column prop="batchNo" label="批次号" width="120" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.batchNo || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="unit" label="单位" width="60">
        <template #default="{ row }">
          {{ row.unit || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="150" class-name="mono-num" />
      <el-table-column label="操作" min-width="170" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button type="primary" link size="small" title="查看库存详情" @click="handleView(row)">详情</el-button>
            <el-button type="primary" link size="small" title="编辑库存记录" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该库存记录吗？" confirm-button-type="danger" @confirm="handleDelete(row.inventoryId!)">
              <template #reference>
                <el-button type="danger" link size="small" title="删除库存记录">删除</el-button>
              </template>
            </el-popconfirm>
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

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑库存记录' : '新增库存记录'"
      width="540px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="物料" prop="itemId">
          <el-select
            v-model="form.itemId"
            placeholder="请选择物料"
            filterable
            clearable
            style="width: 100%"
            :disabled="isEdit"
          >
            <el-option
              v-for="m in materialOptions"
              :key="m.itemId"
              :label="`${m.itemCode} - ${m.itemName}`"
              :value="m.itemId!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="库位" prop="locationId">
          <el-select
            v-model="form.locationId"
            placeholder="请选择库位"
            filterable
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="l in locationOptions"
              :key="l.locationId"
              :label="`${l.locationCode} - ${l.locationName}`"
              :value="l.locationId!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="库存数量" prop="qty">
          <el-input-number
            v-model="form.qty"
            :min="0"
            :precision="4"
            :step="1"
            placeholder="请输入库存数量"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="锁定数量" prop="lockedQty">
          <el-input-number
            v-model="form.lockedQty"
            :min="0"
            :precision="4"
            :step="1"
            placeholder="请输入锁定数量"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="批次号" prop="batchNo">
          <el-input v-model="form.batchNo" placeholder="请输入批次号" maxlength="64" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>

    <!-- 查看详情对话框（只读） -->
    <el-dialog
      v-model="detailVisible"
      title="库存详情"
      width="540px"
      :close-on-click-modal="false"
    >
      <el-descriptions v-if="detailRow" :column="2" border>
        <el-descriptions-item label="ID">{{ detailRow.inventoryId }}</el-descriptions-item>
        <el-descriptions-item label="物料编码">{{ detailRow.itemCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="物料名称">{{ detailRow.itemName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="库位编码">{{ detailRow.locationCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="库位名称">{{ detailRow.locationName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="单位">{{ detailRow.unit || '-' }}</el-descriptions-item>
        <el-descriptions-item label="库存数量">{{ detailRow.qty }}</el-descriptions-item>
        <el-descriptions-item label="锁定数量">{{ detailRow.lockedQty }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ detailRow.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detailRow.updateTime || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, onUnmounted } from 'vue'
import { onWsConnected, useWebSocket } from '@/utils/useWebSocket'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import PageContainer from '@/components/PageContainer.vue'
import {
  getWmsInventoryList,
  createWmsInventory,
  updateWmsInventory,
  deleteWmsInventory
} from '@/api/wmsInventory'
import { getBaseItemList } from '@/api/baseItem'
import { getWmsLocationList } from '@/api/wmsLocation'
import type { WmsInventory, BaseItem, WmsLocation } from '@/types/api'

// ==================== 响应式状态 ====================

const tableData = ref<WmsInventory[]>([])
const materialOptions = ref<BaseItem[]>([])
const locationOptions = ref<WmsLocation[]>([])
const loading = ref(false)
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editId = ref<number>()

// 详情对话框
const detailVisible = ref(false)
const detailRow = ref<WmsInventory | null>(null)

const query = reactive({ page: 1, size: 15 })

const searchForm = reactive({
  itemCode: '',
  itemName: '',
  warehouseName: ''
})

const form = reactive<WmsInventory>({
  itemId: undefined,
  locationId: undefined,
  qty: 0,
  lockedQty: 0,
  batchNo: ''
})

// ==================== 表单校验规则 ====================

const rules: FormRules = {
  itemId: [
    { required: true, message: '请选择物料', trigger: 'change' }
  ],
  locationId: [
    { required: true, message: '请选择库位', trigger: 'change' }
  ],
  qty: [
    { required: true, message: '请输入库存数量', trigger: 'change' }
  ]
}

// ==================== 生命周期 ====================

onMounted(() => {
  fetchList()
  loadMaterialOptions()
  loadLocationOptions()
  const ws = useWebSocket()
  ws.connect()
  offWs = ws.onWsMessage('INVENTORY', () => fetchList())
  offWsConnected = onWsConnected(() => fetchList())
})

onUnmounted(() => {
  listDisposed = true
  listRequestSequence += 1
  offWs?.()
  offWsConnected?.()
})

let offWs: (() => void) | undefined
let offWsConnected: (() => void) | undefined
let listRequestSequence = 0
let listDisposed = false

// ==================== 数据获取 ====================

async function fetchList() {
  if (listDisposed) return
  const requestSequence = ++listRequestSequence
  loading.value = true
  try {
    const params: Record<string, unknown> = { page: query.page, size: query.size }
    if (searchForm.itemCode) params.itemCode = searchForm.itemCode
    if (searchForm.itemName) params.itemName = searchForm.itemName
    if (searchForm.warehouseName) params.warehouseName = searchForm.warehouseName

    const res = await getWmsInventoryList(params)
    if (listDisposed || requestSequence !== listRequestSequence) return
    tableData.value = Array.isArray(res?.records) ? res.records : []
    total.value = res?.total ?? 0
  } catch {
    // 错误已在 request 拦截器中统一处理
  } finally {
    if (!listDisposed && requestSequence === listRequestSequence) loading.value = false
  }
}

/** 加载物料下拉选项 */
async function loadMaterialOptions() {
  try {
    const res = await getBaseItemList({ page: 1, size: 9999 })
    materialOptions.value = Array.isArray(res?.records) ? res.records : []
  } catch {
    // 加载失败不影响主列表
  }
}

/** 加载库位下拉选项 */
async function loadLocationOptions() {
  try {
    const res = await getWmsLocationList({ page: 1, size: 9999 })
    locationOptions.value = Array.isArray(res?.records) ? res.records : []
  } catch {
    // 加载失败不影响主列表
  }
}

// ==================== 详情 ====================

function handleView(row: WmsInventory) {
  detailRow.value = row
  detailVisible.value = true
}

// ==================== 搜索 ====================

function handleSearch() {
  query.page = 1
  fetchList()
}

function resetSearch() {
  searchForm.itemCode = ''
  searchForm.itemName = ''
  searchForm.warehouseName = ''
  query.page = 1
  fetchList()
}

// ==================== 表单操作 ====================

function resetForm() {
  form.itemId = undefined
  form.locationId = undefined
  form.qty = 0
  form.lockedQty = 0
  form.batchNo = ''
  formRef.value?.clearValidate()
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: WmsInventory) {
  isEdit.value = true
  editId.value = row.inventoryId
  form.itemId = row.itemId
  form.locationId = row.locationId
  form.qty = row.qty
  form.lockedQty = row.lockedQty
  form.batchNo = row.batchNo
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = { ...form }

    if (isEdit.value && editId.value) {
      await updateWmsInventory(editId.value, payload)
      ElMessage.success('编辑成功')
    } else {
      await createWmsInventory(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch {
    // 错误已在 request 拦截器中统一处理
  } finally {
    submitting.value = false
  }
}

// ==================== 删除 ====================

async function handleDelete(id: number) {
  try {
    await deleteWmsInventory(id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // 错误已在 request 拦截器中统一处理
  }
}
</script>

<style scoped>
.cell-sub {
  margin-left: 4px;
  color: var(--mes-text-muted);
}
/* 工具条仅放主操作：右对齐 */
.table-toolbar {
  justify-content: flex-end;
}
</style>
