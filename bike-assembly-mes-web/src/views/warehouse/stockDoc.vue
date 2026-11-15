<template>
  <PageContainer title="出入库单" description="管理收货、发料、调拨与盘点单据。">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="searchForm.docNo" placeholder="单据编号" clearable />
      <el-select v-model="searchForm.docType" placeholder="单据类型" clearable>
        <el-option label="入库" value="I" />
        <el-option label="出库" value="O" />
        <el-option label="移库" value="T" />
      </el-select>
      <el-select v-model="searchForm.status" placeholder="状态" clearable>
        <el-option label="草稿" value="0" />
        <el-option label="已提交" value="1" />
        <el-option label="已审核" value="2" />
        <el-option label="已作废" value="3" />
      </el-select>
      <el-input v-model="searchForm.keyword" placeholder="明细关键词" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-actions">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增单据
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="docId" label="ID" width="60" class-name="mono-num" />
      <el-table-column prop="docNo" label="单据编号" width="150" show-overflow-tooltip />
      <el-table-column label="单据类型" width="80">
        <template #default="{ row }">
          <el-tag :type="docTypeTag(row.docType)" size="small">
            {{ docTypeLabel(row.docType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="relatedOrderNo" label="关联单号" width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.relatedOrderNo || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="明细" min-width="190" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="detail-summary">{{ formatDetailsSummary(row.detailsJson) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="160" class-name="mono-num" />
      <el-table-column label="操作" min-width="190" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-popconfirm
              v-if="row.status === '0' || row.status === '1'"
              title="执行后将增减库存并写入流水，确认执行？"
              @confirm="handleExecute(row.docId!)"
            >
              <template #reference>
                <el-button type="success" link size="small" title="执行单据">执行</el-button>
              </template>
            </el-popconfirm>
            <el-button type="primary" link size="small" title="查看单据详情" @click="handleView(row)">详情</el-button>
            <el-button type="primary" link size="small" title="编辑单据" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该单据吗？" confirm-button-type="danger" @confirm="handleDelete(row.docId!)">
              <template #reference>
                <el-button type="danger" link size="small" title="删除单据">删除</el-button>
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
      :title="isEdit ? '编辑单据' : '新增单据'"
      width="700px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="单据编号" prop="docNo">
          <el-input v-model="form.docNo" placeholder="请输入单据编号" maxlength="64" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="单据类型" prop="docType">
              <el-select v-model="form.docType" placeholder="请选择" style="width:100%">
                <el-option label="入库 (I)" value="I" />
                <el-option label="出库 (O)" value="O" />
                <el-option label="移库 (T)" value="T" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择" style="width:100%">
                <el-option label="草稿" value="0" />
                <el-option label="已提交" value="1" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="关联单号" prop="relatedOrderNo">
          <el-input v-model="form.relatedOrderNo" placeholder="请输入关联单号（可选）" maxlength="64" />
        </el-form-item>

        <!-- 明细行 -->
        <el-form-item label="明细">
          <div class="detail-lines">
            <div
              v-for="(line, idx) in form.detailLines"
              :key="idx"
              class="detail-line"
            >
              <el-select
                v-model="line.itemId"
                placeholder="物料"
                filterable
                clearable
                style="width:170px"
                @change="(val: number | undefined) => onDetailItemChange(idx, val)"
              >
                <el-option
                  v-for="m in materialOptions"
                  :key="m.itemId"
                  :label="`${m.itemCode} - ${m.itemName}`"
                  :value="m.itemId!"
                />
              </el-select>
              <el-select
                v-model="line.locationId"
                placeholder="库位"
                filterable
                clearable
                style="width:160px"
              >
                <el-option
                  v-for="l in locationOptions"
                  :key="l.locationId"
                  :label="`${l.locationCode} - ${l.locationName}`"
                  :value="l.locationId!"
                />
              </el-select>
              <el-input-number
                v-model="line.qty"
                :min="0.0001"
                :precision="4"
                placeholder="数量"
                controls-position="right"
                style="width:120px"
              />
              <el-input
                v-model="line.batchNo"
                placeholder="批次号"
                maxlength="64"
                style="width:110px"
              />
              <el-select
                v-if="form.docType === 'T'"
                v-model="line.targetLocationId"
                placeholder="目标库位"
                filterable
                clearable
                style="width:160px"
              >
                <el-option
                  v-for="l in locationOptions"
                  :key="l.locationId"
                  :label="`${l.locationCode} - ${l.locationName}`"
                  :value="l.locationId!"
                />
              </el-select>
              <el-button
                type="danger"
                :icon="Delete"
                circle
                size="small"
                aria-label="删除明细行"
                title="删除明细行"
                :disabled="form.detailLines.length <= 1"
                @click="removeDetailLine(idx)"
              />
            </div>
            <el-button type="primary" link size="small" @click="addDetailLine">
              <el-icon><Plus /></el-icon> 添加明细行
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框（只读） -->
    <el-dialog
      v-model="detailVisible"
      title="单据详情"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-descriptions v-if="detailRow" :column="2" border>
        <el-descriptions-item label="单据ID">{{ detailRow.docId }}</el-descriptions-item>
        <el-descriptions-item label="单据编号">{{ detailRow.docNo }}</el-descriptions-item>
        <el-descriptions-item label="单据类型">
          <el-tag :type="docTypeTag(detailRow.docType)" size="small">
            {{ docTypeLabel(detailRow.docType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(detailRow.status)" size="small">
            {{ statusLabel(detailRow.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="关联单号">{{ detailRow.relatedOrderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailRow.createTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 明细表格 -->
      <div v-if="detailRow" class="detail-section">
        <h4 class="detail-heading">明细列表</h4>
        <el-table :data="parseDetails(detailRow.detailsJson)" border size="small" max-height="300">
          <el-table-column prop="itemCode" label="物料编码" width="120" show-overflow-tooltip />
          <el-table-column prop="itemName" label="物料名称" min-width="100" show-overflow-tooltip />
          <el-table-column prop="locationCode" label="库位编码" width="120" show-overflow-tooltip />
          <el-table-column prop="locationName" label="库位名称" min-width="100" show-overflow-tooltip />
          <el-table-column prop="qty" label="数量" width="100" class-name="mono-num" />
          <el-table-column prop="batchNo" label="批次号" width="120" show-overflow-tooltip>
            <template #default="{ row: drow }">
              {{ drow.batchNo || '-' }}
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无数据" :image-size="72" />
          </template>
        </el-table>
      </div>
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
import { Delete } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import PageContainer from '@/components/PageContainer.vue'
import {
  getWmsStockDocList,
  createWmsStockDoc,
  updateWmsStockDoc,
  deleteWmsStockDoc,
  executeStockDoc
} from '@/api/wmsStockDoc'
import { getBaseItemList } from '@/api/baseItem'
import { getWmsLocationList } from '@/api/wmsLocation'
import type { WmsStockDoc, BaseItem, WmsLocation } from '@/types/api'

// ==================== 明细行类型 ====================

interface DetailLine {
  itemId?: number
  itemCode?: string
  itemName?: string
  locationId?: number
  locationCode?: string
  locationName?: string
  targetLocationId?: number
  qty: number
  batchNo: string
}

function emptyDetailLine(): DetailLine {
  return {
    itemId: undefined,
    itemCode: '',
    itemName: '',
    locationId: undefined,
    locationCode: '',
    locationName: '',
    targetLocationId: undefined,
    qty: 1,
    batchNo: ''
  }
}

// ==================== 响应式状态 ====================

const tableData = ref<WmsStockDoc[]>([])
const materialOptions = ref<BaseItem[]>([])
const locationOptions = ref<WmsLocation[]>([])
const loading = ref(false)
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editId = ref<number>()

const detailVisible = ref(false)
const detailRow = ref<WmsStockDoc | null>(null)

const query = reactive({ page: 1, size: 15 })

const searchForm = reactive({
  docNo: '',
  docType: '',
  status: '',
  keyword: ''
})

interface StockDocForm {
  docNo: string
  docType: string
  relatedOrderNo: string
  status: string
  detailLines: DetailLine[]
}

const form = reactive<StockDocForm>({
  docNo: '',
  docType: '',
  relatedOrderNo: '',
  status: '0',
  detailLines: [emptyDetailLine()]
})

// ==================== 表单校验规则 ====================

const rules: FormRules = {
  docNo: [
    { required: true, message: '请输入单据编号', trigger: 'blur' }
  ],
  docType: [
    { required: true, message: '请选择单据类型', trigger: 'change' }
  ],
  status: [
    { required: true, message: '请选择状态', trigger: 'change' }
  ]
}

// ==================== 标签映射 ====================

function docTypeLabel(code: string): string {
  const map: Record<string, string> = { I: '入库', O: '出库', T: '移库' }
  return map[code] || code || '-'
}

function docTypeTag(code: string): 'success' | 'warning' | 'info' | '' {
  if (code === 'I') return 'success'
  if (code === 'O') return 'warning'
  if (code === 'T') return 'info'
  return 'info'
}

function statusLabel(code: string): string {
  const map: Record<string, string> = { '0': '草稿', '1': '已提交', '2': '已执行', '3': '已作废' }
  return map[code] || code || '-'
}

function statusTag(code: string): 'info' | 'warning' | 'success' | 'danger' | '' {
  const map: Record<string, string> = { '0': 'info', '1': 'warning', '2': 'success', '3': 'danger' }
  return (map[code] || 'info') as 'info' | 'warning' | 'success' | 'danger' | ''
}

// ==================== 明细处理 ====================

/** 序列化明细行 → detailsJson 字符串 */
function serializeDetails(lines: DetailLine[]): string {
  const items = lines
    .filter(l => l.itemId)
    .map(l => ({
      itemId: l.itemId,
      itemCode: l.itemCode || '',
      itemName: l.itemName || '',
      locationId: l.locationId || undefined,
      locationCode: l.locationCode || '',
      locationName: l.locationName || '',
      ...(l.targetLocationId ? { targetLocationId: l.targetLocationId } : {}),
      qty: l.qty,
      batchNo: l.batchNo || undefined
    }))
  return JSON.stringify(items)
}

/** 反序列化 detailsJson → 明细行数组 */
function deserializeDetails(json: string): DetailLine[] {
  try {
    const arr = JSON.parse(json)
    if (!Array.isArray(arr) || arr.length === 0) return [emptyDetailLine()]
    return arr.map((d: Record<string, unknown>) => ({
      itemId: d.itemId as number | undefined,
      itemCode: (d.itemCode as string) || '',
      itemName: (d.itemName as string) || '',
      locationId: d.locationId as number | undefined,
      locationCode: (d.locationCode as string) || '',
      locationName: (d.locationName as string) || '',
      targetLocationId: d.targetLocationId as number | undefined,
      qty: (d.qty as number) || 0,
      batchNo: (d.batchNo as string) || ''
    }))
  } catch {
    return [emptyDetailLine()]
  }
}

/** 解析详情JSON用于只读展示 */
function parseDetails(json: string): DetailLine[] {
  return deserializeDetails(json)
}

/** 表格中显示明细摘要 */
function formatDetailsSummary(json: string): string {
  try {
    const arr = JSON.parse(json)
    if (!Array.isArray(arr)) return json
    return arr.map((d: Record<string, unknown>) => {
      const code = d.itemCode || ''
      const loc = d.locationCode || ''
      const q = d.qty || 0
      const b = d.batchNo ? `(${d.batchNo})` : ''
      return `${code}${loc ? '@' + loc : ''} x${q}${b}`
    }).join('; ')
  } catch {
    return json
  }
}

function onDetailItemChange(idx: number, itemId: number | undefined) {
  const line = form.detailLines[idx]
  if (itemId) {
    const mat = materialOptions.value.find(m => m.itemId === itemId)
    if (mat) {
      line.itemCode = mat.itemCode
      line.itemName = mat.itemName
    }
  } else {
    line.itemCode = ''
    line.itemName = ''
  }
}

function addDetailLine() {
  form.detailLines.push(emptyDetailLine())
}

function removeDetailLine(idx: number) {
  if (form.detailLines.length > 1) {
    form.detailLines.splice(idx, 1)
  }
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
    if (searchForm.docNo) params.docNo = searchForm.docNo
    if (searchForm.docType) params.docType = searchForm.docType
    if (searchForm.status) params.status = searchForm.status
    if (searchForm.keyword) params.keyword = searchForm.keyword

    const res = await getWmsStockDocList(params)
    if (listDisposed || requestSequence !== listRequestSequence) return
    tableData.value = Array.isArray(res?.records) ? res.records : []
    total.value = res?.total ?? 0
  } catch {
    // 错误已在 request 拦截器中统一处理
  } finally {
    if (!listDisposed && requestSequence === listRequestSequence) loading.value = false
  }
}

async function loadMaterialOptions() {
  try {
    const res = await getBaseItemList({ page: 1, size: 9999 })
    materialOptions.value = Array.isArray(res?.records) ? res.records : []
  } catch { /* ignore */ }
}

async function loadLocationOptions() {
  try {
    const res = await getWmsLocationList({ page: 1, size: 9999 })
    locationOptions.value = Array.isArray(res?.records) ? res.records : []
  } catch { /* ignore */ }
}

// ==================== 搜索 ====================

function handleSearch() {
  query.page = 1
  fetchList()
}

function resetSearch() {
  searchForm.docNo = ''
  searchForm.docType = ''
  searchForm.status = ''
  searchForm.keyword = ''
  query.page = 1
  fetchList()
}

// ==================== 详情 ====================

function handleView(row: WmsStockDoc) {
  detailRow.value = row
  detailVisible.value = true
}

// ==================== 表单操作 ====================

function resetForm() {
  form.docNo = ''
  form.docType = ''
  form.relatedOrderNo = ''
  form.status = '0'
  form.detailLines = [emptyDetailLine()]
  formRef.value?.clearValidate()
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: WmsStockDoc) {
  isEdit.value = true
  editId.value = row.docId
  form.docNo = row.docNo
  form.docType = row.docType
  form.relatedOrderNo = row.relatedOrderNo
  form.status = row.status
  form.detailLines = deserializeDetails(row.detailsJson)
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const detailsJson = serializeDetails(form.detailLines)
    const payload = {
      docNo: form.docNo,
      docType: form.docType,
      relatedOrderNo: form.relatedOrderNo,
      status: form.status,
      detailsJson
    }

    if (isEdit.value && editId.value) {
      await updateWmsStockDoc(editId.value, payload)
      ElMessage.success('编辑成功')
    } else {
      await createWmsStockDoc(payload)
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
    await deleteWmsStockDoc(id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // 错误已在 request 拦截器中统一处理
  }
}

// ==================== 执行单据 ====================

async function handleExecute(id: number) {
  try {
    await executeStockDoc(id)
    ElMessage.success('单据已执行，库存已更新')
    fetchList()
  } catch {
    // 错误已在 request 拦截器中统一处理
  }
}
</script>

<style scoped>
.detail-lines {
  width: 100%;
}

.detail-line {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.detail-summary {
  color: var(--mes-text-secondary);
}

.detail-section {
  margin-top: 16px;
}

.detail-heading {
  margin-bottom: 8px;
  color: var(--mes-text-primary);
  font-size: 15px;
  font-weight: 650;
}

/* 工具条仅放主操作：右对齐 */
.table-toolbar {
  justify-content: flex-end;
}

@media (max-width: 640px) {
  .detail-line {
    gap: 6px;
    margin-bottom: 6px;
  }
}
</style>
