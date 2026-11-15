<template>
  <view class="qc-page">
    <!-- 顶部摘要 -->
    <view class="summary-bar">
      <view class="summary-item">
        <text class="summary-num">{{ summary.total }}</text>
        <text class="summary-label">全部</text>
      </view>
      <view class="summary-item">
        <text class="summary-num summary-num--pending">{{ summary.pending }}</text>
        <text class="summary-label">待检</text>
      </view>
      <view class="summary-item">
        <text class="summary-num summary-num--pass">{{ summary.pass }}</text>
        <text class="summary-label">合格</text>
      </view>
      <view class="summary-item">
        <text class="summary-num summary-num--fail">{{ summary.fail }}</text>
        <text class="summary-label">不合格</text>
      </view>
    </view>

    <view class="filter-bar">
      <view class="filter-heading">
        <text class="filter-heading-title">筛选质检任务</text>
        <button class="filter-reset" :disabled="!hasFilters" @click="resetFilters">重置</button>
      </view>
      <view v-if="scanWorkOrderId" class="filter-row">
        <text class="filter-count">扫码定位：{{ selectedWorkOrderLabel }}</text>
        <button class="filter-reset" @click="clearScanLocation">清除扫码定位</button>
      </view>
      <button class="work-order-trigger" @click="openWorkOrderFilter">
        <text class="work-order-trigger-label">{{ selectedWorkOrderLabel || '全部工单' }}</text>
        <text class="work-order-trigger-action">搜索 / 选择</text>
      </button>
      <view v-for="group in filterGroups" :key="group.key" class="filter-row">
        <text class="filter-label">{{ group.label }}</text>
        <view class="filter-options">
          <button v-for="option in group.options" :key="option.value"
            class="filter-option" :class="{ 'filter-option--active': filters[group.key] === option.value }"
            :aria-pressed="filters[group.key] === option.value" @click="setFilter(group.key, option.value)">
            {{ option.label }}
          </button>
        </view>
      </view>
      <text class="filter-count">符合条件 {{ filteredList.length }} 条 / 共 {{ inspectionList.length }} 条</text>
    </view>

    <view v-if="showWorkOrderFilter" class="modal-mask" @click="showWorkOrderFilter = false">
      <view class="modal-panel order-filter-panel" @click.stop>
        <view class="modal-header">
          <text class="modal-title">选择工单</text>
          <button class="order-close" @click="showWorkOrderFilter = false">关闭</button>
        </view>
        <view class="order-search-wrapper">
          <input v-model="workOrderKeyword" class="order-search" placeholder="输入工单编号或工单 ID" :maxlength="80" />
          <button v-if="workOrderKeyword" class="order-search-clear" @click="workOrderKeyword = ''">清空</button>
        </view>
        <scroll-view scroll-y class="order-options">
          <button class="order-option" :class="{ 'order-option--active': !filters.workOrderId }" @click="selectWorkOrder(0)">
            <text>全部工单</text><text class="order-count">{{ inspectionList.length }} 条质检任务</text>
          </button>
          <button v-for="option in searchedWorkOrders" :key="option.value" class="order-option"
            :class="{ 'order-option--active': filters.workOrderId === option.value }" @click="selectWorkOrder(option.value)">
            <text>{{ option.label }}</text><text class="order-count">{{ option.count }} 条质检任务</text>
          </button>
          <text v-if="searchedWorkOrders.length === 0" class="order-empty">没有匹配的工单，请更换关键词</text>
        </scroll-view>
      </view>
    </view>

    <!-- 加载中 -->
    <view v-if="loading" class="state-box">
      <text class="state-text">加载中...</text>
    </view>

    <!-- 加载失败 -->
    <view v-else-if="loadError" class="state-box">
      <text class="state-text state-text--error">{{ loadError }}</text>
      <view class="state-retry-btn" hover-class="state-retry-btn--hover" @click="fetchData">
        <text class="retry-text">重新加载</text>
      </view>
    </view>

    <!-- 空数据 -->
    <view v-else-if="filteredList.length === 0" class="state-box">
      <image class="empty-img" src="/static/icons/empty-box.svg" mode="aspectFit" />
      <text class="empty-title">{{ hasFilters ? '没有符合筛选条件的质检任务' : '暂无质检记录' }}</text>
      <text class="empty-desc">{{ scanWorkOrderId ? '当前工单暂无符合条件的质检任务，可调整筛选或清除扫码定位' : hasFilters ? '请调整筛选条件或重置后查看全部任务' : '报工送检后系统会自动生成质检任务' }}</text>
      <button v-if="hasFilters" class="empty-reset" @click="resetFilters">重置筛选</button>
    </view>

    <!-- 质检列表 -->
    <view v-else class="inspection-list">
      <view
        v-for="insp in filteredList"
        :key="insp.inspectionId"
        class="inspection-card"
        @click="toggleExpand(insp.inspectionId)"
      >
        <view class="card-header">
          <view class="header-left">
            <text class="inspection-no">{{ insp.inspectionNo }}</text>
            <text class="wo-no">{{ getWorkOrderNo(insp.workOrderId) }}</text>
          </view>
          <view class="header-right">
            <text class="status-tag" :class="'status--' + insp.status">
              {{ getInspectionStatusLabel(insp.status) }}
            </text>
            <text class="result-tag" :class="getInspectionResultClass(insp.result)">
              {{ getInspectionResultLabel(insp.result) }}
            </text>
            <text class="expand-icon">{{ expandedIds.has(insp.inspectionId) ? '▴' : '▾' }}</text>
          </view>
        </view>

        <view class="card-body">
          <view class="info-row">
            <text class="info-label">工序</text>
            <text class="info-value">{{ getProcessName(insp.processId) }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">检验人</text>
            <text class="info-value">{{ insp.inspector || '—' }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">检验时间</text>
            <text class="info-value">{{ formatTime(insp.inspectionTime) }}</text>
          </view>
        </view>

        <!-- 操作按钮 -->
        <view class="card-actions" v-if="insp.status !== '2'">
          <button
            v-if="insp.status === '0' && hasPermission('app:inspection:create')"
            class="action-btn action-btn--enter"
            @click.stop="openResultForm(insp)"
          >
            录入检验结果
          </button>
          <button
            v-if="insp.status === '1' && hasPermission('app:inspection:confirm')"
            class="action-btn action-btn--confirm"
            @click.stop="handleConfirm(insp)"
          >
            确认结果
          </button>
        </view>

        <!-- 展开质检明细 -->
        <view v-if="expandedIds.has(insp.inspectionId)" class="card-detail">
          <text class="detail-title">质检明细</text>
          <view
            v-for="(item, idx) in parseDetails(insp.detailsJson)"
            :key="idx"
            class="detail-item"
          >
            <view class="detail-item-header">
              <text class="detail-item-name">{{ item.itemName }}</text>
              <text class="detail-item-result" :class="getInspectionResultClass(item.result)">
                {{ getInspectionResultLabel(item.result) }}
              </text>
            </view>
            <text class="detail-item-standard">标准：{{ item.standard }}</text>
            <text class="detail-item-actual">实测：{{ item.actual || '—' }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- ========== 录入检验结果弹窗（更新已有待检质检单） ========== -->
    <view v-if="showResultForm" class="modal-mask" @click="showResultForm = false">
      <view class="modal-panel" @click.stop>
        <view class="modal-header">
          <text class="modal-title">录入检验结果</text>
          <text class="modal-close" @click="showResultForm = false">✕</text>
        </view>
        <scroll-view scroll-y class="modal-body">
          <view class="form-item">
            <text class="form-label">质检单号：{{ editingInspection?.inspectionNo }}</text>
          </view>
          <view class="form-item">
            <text class="form-label">工序：{{ getProcessName(editingInspection?.processId || null) }}</text>
          </view>

          <view v-if="inspectionItems.length > 0" class="inspection-items-section">
            <text class="section-title">检验项目</text>
            <view
              v-for="(item, idx) in inspectionItems"
              :key="item.inspectionItemId"
              class="qc-item-block"
            >
              <view class="qc-item-header">
                <text class="qc-item-name">{{ item.itemName }}</text>
                <view class="qc-item-result-toggle">
                  <view
                    class="result-opt result-opt--pass"
                    :class="{ 'result-opt--active': itemResults[idx] === 'P' }"
                    @click="setItemResult(idx, 'P')"
                  >
                    合格
                  </view>
                  <view
                    class="result-opt result-opt--fail"
                    :class="{ 'result-opt--active': itemResults[idx] === 'F' }"
                    @click="setItemResult(idx, 'F')"
                  >
                    不合格
                  </view>
                </view>
              </view>
              <text class="qc-item-standard">标准：{{ item.standard || '—' }}</text>
              <input
                class="form-input form-input--small"
                v-model="itemActuals[idx]"
                placeholder="请输入实测情况"
                placeholder-style="color: #a7b1c2"
              />
            </view>

            <view class="overall-result">
              <text class="overall-label">综合判定：</text>
              <text class="result-tag result-tag--lg" :class="getInspectionResultClass(overallResult)">
                {{ getInspectionResultLabel(overallResult) }}
              </text>
            </view>
          </view>

          <view v-if="inspectionItemsLoaded && inspectionItems.length === 0" class="empty-items">
            <text class="empty-items-text">该工序暂无质检项标准</text>
          </view>
          <view v-if="inspectionItems.length > 0 && overallResult === '0'" class="submit-hint">
            <text class="submit-hint-text">请至少为一个检验项选择合格/不合格后提交</text>
          </view>
          <view v-if="inspectionItemsLoaded && inspectionItems.length === 0" class="form-item overall-manual">
            <text class="form-label">综合判定（该工序暂无质检项标准，可直接判定）</text>
            <view class="qc-item-result-toggle">
              <view
                class="result-opt result-opt--pass"
                :class="{ 'result-opt--active': overallManual === 'P' }"
                @click="overallManual = 'P'"
              >
                合格
              </view>
              <view
                class="result-opt result-opt--fail"
                :class="{ 'result-opt--active': overallManual === 'F' }"
                @click="overallManual = 'F'"
              >
                不合格
              </view>
            </view>
          </view>
        </scroll-view>
        <view class="modal-footer">
          <button class="modal-btn modal-btn--cancel" @click="showResultForm = false">取消</button>
          <button class="modal-btn modal-btn--confirm" :disabled="submitting || !canSubmitResult" @click="submitResult">
            {{ submitting ? '提交中...' : '确认录入' }}
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad, onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'
import { hasPermission } from '@/utils/permission'
import { parsePositiveSafeId } from '@/utils/workOrderScan'
import {
  getInspectionListApi, getInspectionItemListApi,
  enterInspectionResultApi, confirmInspectionApi,
} from '@/api/inspection'
import { getBaseProcessListApi } from '@/api/reference'
import {
  getInspectionResultLabel,
  getInspectionResultClass,
  getInspectionStatusLabel,
} from '@/types/inspection'
import type {
  QmsInspectionVO,
  QmsInspectionItemVO,
  InspectionDetailItem,
} from '@/types/inspection'
import { filterInspections, getInspectionWorkOrders, type InspectionFilters } from '@/utils/inspectionFilters'
import type { BaseProcessVO } from '@/types/reference'

const userStore = useUserStore()

const loading = ref(false)
const loadError = ref('')
const paramError = ref('')
const scanWorkOrderId = ref<number | null>(null)
const submitting = ref(false)
let fetchGeneration = 0

const inspectionList = ref<QmsInspectionVO[]>([])
const processRefs = ref<BaseProcessVO[]>([])
const inspectionItems = ref<QmsInspectionItemVO[]>([])
const inspectionItemsLoaded = ref(false)

const filters = reactive<InspectionFilters>({ workOrderId: 0, stage: '', confirmation: '', result: '' })
const showWorkOrderFilter = ref(false)
const workOrderKeyword = ref('')
type FilterKey = 'stage' | 'confirmation' | 'result'
const filterGroups: Array<{ key: FilterKey; label: string; options: Array<{ value: string; label: string }> }> = [
  { key: 'stage', label: '质检状态', options: [{ value: '', label: '全部' }, { value: 'pending', label: '待质检' }, { value: 'inspected', label: '已质检' }] },
  { key: 'confirmation', label: '确认状态', options: [{ value: '', label: '全部' }, { value: 'unconfirmed', label: '未确认' }, { value: 'confirmed', label: '已确认' }] },
  { key: 'result', label: '检验结果', options: [{ value: '', label: '全部' }, { value: 'P', label: '合格' }, { value: 'F', label: '不合格' }] },
]
const hasFilters = computed(() => !!(filters.workOrderId || filters.stage || filters.confirmation || filters.result))
const expandedIds = ref(new Set<number>())

// 当前操作的质检单
const editingInspection = ref<QmsInspectionVO | null>(null)
const showResultForm = ref(false)

const itemResults = ref<string[]>([])
const itemActuals = ref<string[]>([])
const overallManual = ref('')

const summary = computed(() => {
  const total = inspectionList.value.length
  const pending = inspectionList.value.filter((i) => i.status === '0').length
  const entered = inspectionList.value.filter((i) => i.status === '1').length
  const confirmed = inspectionList.value.filter((i) => i.status === '2').length
  const pass = inspectionList.value.filter((i) => i.result === 'P').length
  const fail = inspectionList.value.filter((i) => i.result === 'F').length
  return { total, pending, entered, confirmed, pass, fail }
})

const filteredList = computed(() => {
  return filterInspections(inspectionList.value, filters).sort((a, b) => {
    if (a.inspectionTime && b.inspectionTime) {
      return b.inspectionTime.localeCompare(a.inspectionTime)
    }
    return 0
  })
})

const workOrderOptions = computed(() => getInspectionWorkOrders(inspectionList.value))
const searchedWorkOrders = computed(() => {
  const keyword = workOrderKeyword.value.trim().toLowerCase()
  return workOrderOptions.value.filter((option) => !keyword || option.label.toLowerCase().includes(keyword) || String(option.value).includes(keyword))
})
const selectedWorkOrderLabel = computed(() => workOrderOptions.value.find((option) => option.value === filters.workOrderId)?.label || (filters.workOrderId ? `工单 #${filters.workOrderId}` : ''))

const overallResult = computed(() => {
  const selected = itemResults.value.filter((r) => !!r)
  if (selected.length === 0) return '0'
  if (selected.includes('F')) return 'F'
  return 'P'
})

const canSubmitResult = computed(() => {
  if (!inspectionItemsLoaded.value) return false
  if (inspectionItems.value.length > 0) {
    return overallResult.value === 'P' || overallResult.value === 'F'
  }
  return overallManual.value === 'P' || overallManual.value === 'F'
})

onLoad((options) => {
  if (options?.workOrderId === undefined) return
  const id = parsePositiveSafeId(options.workOrderId)
  if (id === null) {
    paramError.value = '工单 ID 格式错误，请重新扫码'
    loadError.value = paramError.value
    return
  }
  scanWorkOrderId.value = id
  filters.workOrderId = id
  if (options?.stage === 'pending') filters.stage = 'pending'
})

onShow(() => {
  if (!userStore.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  if (!hasPermission('app:inspection:view')) {
    uni.showToast({ title: '暂无访问权限', icon: 'none' })
    uni.navigateBack()
    return
  }
  fetchData()
})

onPullDownRefresh(() => {
  fetchData().finally(() => uni.stopPullDownRefresh())
})

async function fetchData(): Promise<void> {
  const generation = ++fetchGeneration
  if (paramError.value || !userStore.isLoggedIn || !hasPermission('app:inspection:view')) return
  const requestWorkOrderId = scanWorkOrderId.value ?? undefined
  const requestStatus = requestWorkOrderId && filters.stage === 'pending' ? '0' : undefined
  // 质检员用 inspection:view-all 查全部质检任务。
  const inspScope = hasPermission('app:inspection:view-all') ? 'all' : 'mine'
  loading.value = true
  loadError.value = ''
  try {
    const [inspections, processes] = await Promise.all([
      getInspectionListApi(requestWorkOrderId, inspScope, requestStatus),
      getBaseProcessListApi(),
    ])
    if (generation !== fetchGeneration) return
    inspectionList.value = inspections
    processRefs.value = processes
    if (requestWorkOrderId === undefined && filters.workOrderId && !workOrderOptions.value.some((option) => option.value === filters.workOrderId)) {
      filters.workOrderId = 0
    }
  } catch (error) {
    if (generation === fetchGeneration) {
      loadError.value = error instanceof Error ? error.message : '数据加载失败，请检查网络后重试'
    }
  } finally {
    if (generation === fetchGeneration) loading.value = false
  }
}

function getWorkOrderNo(workOrderId: number): string {
  return workOrderOptions.value.find((option) => option.value === Number(workOrderId))?.label || `工单 #${workOrderId}`
}

function getProcessName(processId: number | null): string {
  if (!processId) return '—'
  const p = processRefs.value.find((p) => p.processId === processId)
  return p ? p.processName : `工序#${processId}`
}

function formatTime(s: string | null): string {
  if (!s) return '—'
  return s.substring(0, 16).replace('T', ' ')
}

function parseDetails(json: string): InspectionDetailItem[] {
  if (!json) return []
  try {
    const parsed = JSON.parse(json)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function toggleExpand(inspectionId: number): void {
  const next = new Set(expandedIds.value)
  if (next.has(inspectionId)) {
    next.delete(inspectionId)
  } else {
    next.add(inspectionId)
  }
  expandedIds.value = next
}

function openWorkOrderFilter(): void {
  workOrderKeyword.value = ''
  showWorkOrderFilter.value = true
}

function selectWorkOrder(workOrderId: number): void {
  // 扫码定位只能通过明确的清除操作解除，避免空态或选择器意外展示其他工单。
  if (scanWorkOrderId.value && workOrderId !== scanWorkOrderId.value) {
    uni.showToast({ title: '请先清除扫码定位，再选择其他工单', icon: 'none' })
    return
  }
  filters.workOrderId = workOrderId
  showWorkOrderFilter.value = false
}

function setFilter(key: FilterKey, value: string): void {
  if (key === 'stage' && (value === '' || value === 'pending' || value === 'inspected')) filters.stage = value
  if (key === 'confirmation' && (value === '' || value === 'unconfirmed' || value === 'confirmed')) filters.confirmation = value
  if (key === 'result' && (value === '' || value === 'P' || value === 'F')) filters.result = value
  if (key === 'stage' && scanWorkOrderId.value) fetchData()
}

function resetFilters(): void {
  Object.assign(filters, { workOrderId: scanWorkOrderId.value ?? 0, stage: '', confirmation: '', result: '' })
  if (scanWorkOrderId.value) fetchData()
}

function clearScanLocation(): void {
  scanWorkOrderId.value = null
  filters.workOrderId = 0
  fetchData()
}

// ==================== 录入检验结果 ====================

async function openResultForm(insp: QmsInspectionVO): Promise<void> {
  editingInspection.value = insp
  itemResults.value = []
  itemActuals.value = []
  overallManual.value = ''
  inspectionItems.value = []
  inspectionItemsLoaded.value = false
  showResultForm.value = true

  uni.showLoading({ title: '加载质检项...', mask: true })
  try {
    const items = await getInspectionItemListApi(insp.processId || undefined)
    inspectionItems.value = items
    itemResults.value = items.map(() => '')
    itemActuals.value = items.map(() => '')
    inspectionItemsLoaded.value = true
    if (items.length === 0) {
      uni.showToast({ title: '该工序暂无质检项', icon: 'none' })
    }
  } catch {
    uni.showToast({ title: '加载质检项失败', icon: 'none' })
    inspectionItemsLoaded.value = true
  } finally {
    uni.hideLoading()
  }
}

function setItemResult(idx: number, value: 'P' | 'F'): void {
  const next = [...itemResults.value]
  next[idx] = value
  itemResults.value = next
}

async function submitResult(): Promise<void> {
  if (!canSubmitResult.value || !editingInspection.value) return

  const hasItems = inspectionItems.value.length > 0
  const result = hasItems ? overallResult.value : overallManual.value
  if (result !== 'P' && result !== 'F') return

  const details: InspectionDetailItem[] = hasItems
    ? inspectionItems.value.map((item, idx) => ({
        itemName: item.itemName,
        standard: item.standard || '',
        result: itemResults.value[idx] as 'P' | 'F',
        actual: itemActuals.value[idx] || '',
      }))
    : []

  submitting.value = true
  try {
    // 更新已有质检单：status 0→1
    await enterInspectionResultApi(editingInspection.value.inspectionId, {
      result,
      detailsJson: JSON.stringify(details),
    })
    uni.showToast({ title: '检验结果已录入', icon: 'success' })
    showResultForm.value = false
    editingInspection.value = null
    await fetchData()
  } catch {
    // error handled by request layer
  } finally {
    submitting.value = false
  }
}

// ==================== 确认结果 ====================

async function handleConfirm(insp: QmsInspectionVO): Promise<void> {
  uni.showModal({
    title: '确认质检结果',
    content: `确认质检单 ${insp.inspectionNo} 的结果为「${getInspectionResultLabel(insp.result)}」？确认后不可修改。`,
    success: async (res) => {
      if (!res.confirm) return
      submitting.value = true
      try {
        await confirmInspectionApi(insp.inspectionId)
        uni.showToast({ title: '已确认', icon: 'success' })
        await fetchData()
      } catch {
        // error handled by request layer
      } finally {
        submitting.value = false
      }
    },
  })
}
</script>

<style scoped>
.qc-page {
  min-height: 100vh;
  background: var(--lux-porcelain-100);
  padding-bottom: 120rpx;
}

/* ========== 摘要栏 ========== */
.summary-bar {
  display: flex;
  background: var(--lux-surface);
  padding: 24rpx 28rpx;
  justify-content: space-around;
  margin-bottom: 0;
}

.summary-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.summary-num {
  font-size: 40rpx;
  font-weight: 500;
  color: var(--lux-ink-900);
}

.summary-num--pass { color: var(--lux-jade); }
.summary-num--fail { color: var(--lux-wine); }
.summary-num--pending { color: var(--lux-ember); }

.summary-label {
  font-size: 24rpx;
  color: var(--lux-text-faint);
  margin-top: 4rpx;
}

/* ========== 筛选栏 ========== */
.filter-bar {
  background: var(--lux-surface);
  padding: 12rpx 28rpx 20rpx;
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.filter-picker {
  flex-shrink: 0;
}

.filter-tag {
  display: inline-flex;
  align-items: center;
  height: 56rpx;
  padding: 0 24rpx;
  font-size: 26rpx;
  color: var(--lux-text);
  background: var(--lux-porcelain-200);
  border-radius: var(--lux-radius-xs);
  transition: all 0.2s ease;
}

.filter-tag--active {
  color: var(--lux-porcelain-50);
  background: var(--lux-ink-900);
}

/* ========== 通用状态 ========== */
.state-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 140rpx 40rpx;
}

.empty-img {
  width: 120rpx;
  height: 120rpx;
  opacity: 0.25;
  margin-bottom: 24rpx;
}

.empty-title {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--lux-text);
  margin-bottom: 10rpx;
}

.empty-desc {
  font-size: 24rpx;
  color: var(--lux-text-faint);
}

.state-text {
  font-size: 28rpx;
  color: var(--lux-text-faint);
}

.state-text--error {
  color: var(--lux-wine);
  margin-bottom: 24rpx;
}

.state-retry-btn {
  width: 220rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--lux-surface);
  border: 1rpx solid var(--lux-gold-600);
  border-radius: var(--lux-radius-sm);
}

.state-retry-btn--hover {
  background: var(--lux-gold-50);
}

.retry-text {
  font-size: 26rpx;
  color: var(--lux-gold-700);
  font-weight: 500;
}

/* ========== 质检列表 ========== */
.inspection-list {
  padding: 16rpx 28rpx;
}

.inspection-card {
  background: var(--lux-surface);
  border-radius: var(--lux-radius-md);
  padding: 28rpx;
  margin-bottom: 16rpx;
  border: 1rpx solid var(--lux-line);
}

.inspection-card:active {
  background: var(--lux-porcelain-200);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16rpx;
}

.header-left {
  display: flex;
  flex-direction: column;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.inspection-no {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--lux-ink-900);
}

.wo-no {
  font-size: 24rpx;
  color: var(--lux-text-faint);
  margin-top: 6rpx;
}

.expand-icon {
  font-size: 22rpx;
  color: var(--lux-text-faint);
  padding: 4rpx;
}

/* 结果标签 */
.result-tag {
  font-size: 22rpx;
  padding: 6rpx 14rpx;
  border-radius: 8rpx;
  font-weight: 500;
  flex-shrink: 0;
}

.result-tag--lg {
  font-size: 26rpx;
  padding: 8rpx 24rpx;
}

.qc--pass { color: var(--lux-jade); background: var(--lux-jade-50); }
.qc--fail { color: var(--lux-wine); background: var(--lux-wine-50); }
.qc--pending { color: var(--lux-ember); background: var(--lux-ember-50); }

/* 状态标签：0=待检, 1=已录入, 2=已确认 */
.status-tag {
  font-size: 20rpx;
  padding: 4rpx 10rpx;
  border-radius: 6rpx;
  font-weight: 500;
  flex-shrink: 0;
}
.status--0 { color: var(--lux-ember); background: var(--lux-ember-50); }
.status--1 { color: var(--lux-gold-600); background: var(--lux-gold-50); }
.status--2 { color: var(--lux-jade); background: var(--lux-jade-50); }

/* 操作按钮 */
.card-actions {
  display: flex;
  gap: 12rpx;
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--lux-line-soft);
}

.action-btn {
  flex: 1;
  height: 64rpx;
  line-height: 64rpx;
  font-size: 24rpx;
  font-weight: 500;
  border-radius: 12rpx;
  border: none;
  text-align: center;
}

.action-btn--enter {
  background: var(--lux-gold-gradient);
  color: #17130B;
}

.action-btn--confirm {
  background: var(--lux-jade);
  color: #17130B;
}

.action-btn:active { opacity: 0.85; }

.card-body {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info-label {
  font-size: 24rpx;
  color: var(--lux-text-faint);
}

.info-value {
  font-size: 24rpx;
  color: var(--lux-text);
}

/* ========== 展开明细 ========== */
.card-detail {
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid var(--lux-line-soft);
}

.detail-title {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--lux-text);
  margin-bottom: 16rpx;
}

.detail-item {
  background: var(--lux-surface-soft);
  border-radius: 12rpx;
  padding: 16rpx 20rpx;
  margin-bottom: 12rpx;
}

.detail-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8rpx;
}

.detail-item-name {
  font-size: 26rpx;
  font-weight: 500;
  color: var(--lux-ink-900);
}

.detail-item-result {
  font-size: 22rpx;
  padding: 4rpx 12rpx;
  border-radius: 6rpx;
}

.detail-item-standard,
.detail-item-actual {
  font-size: 22rpx;
  color: var(--lux-text-faint);
  display: block;
  margin-top: 4rpx;
}

/* ========== 底部新建按钮 ========== */
.bottom-fixed-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 20rpx 28rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  background: var(--lux-surface);
  box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.04);
}

.create-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: var(--lux-gold-gradient);
  color: #17130B;
  font-size: 30rpx;
  font-weight: 600;
  border-radius: var(--lux-radius-sm);
  border: none;
}

.create-btn:active { opacity: 0.85; }

/* ========== 弹窗 ========== */
.modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: flex-end;
  z-index: 100;
}

.modal-panel {
  width: 100%;
  background: var(--lux-surface);
  border-radius: var(--lux-radius-md) var(--lux-radius-md) 0 0;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  padding-bottom: env(safe-area-inset-bottom);
  overflow: hidden;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 32rpx;
  border-bottom: 1rpx solid var(--lux-line-soft);
  flex-shrink: 0;
}

.modal-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--lux-ink-900);
}

.modal-close {
  font-size: 32rpx;
  color: var(--lux-text-faint);
  padding: 8rpx;
  width: 48rpx;
  height: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-body {
  padding: 28rpx 32rpx;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  max-height: 60vh;
}

.modal-footer {
  display: flex;
  gap: 16rpx;
  padding: 24rpx 32rpx;
  border-top: 1rpx solid var(--lux-line-soft);
  flex-shrink: 0;
}

.modal-btn {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 28rpx;
  font-weight: 500;
  border-radius: var(--lux-radius-sm);
  border: none;
  text-align: center;
}

.modal-btn--cancel {
  background: var(--lux-porcelain-200);
  color: var(--lux-text);
}

.modal-btn--confirm {
  background: var(--lux-gold-gradient);
  color: #17130B;
}

.modal-btn--confirm[disabled] {
  opacity: 0.5;
}

/* ========== 表单 ========== */
.form-item {
  margin-bottom: 24rpx;
}

.form-label {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--lux-ink-900);
  margin-bottom: 14rpx;
}

.form-required {
  color: var(--lux-wine);
}

.form-picker {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: var(--lux-surface-soft);
  border-radius: var(--lux-radius-sm);
  padding: 0 24rpx;
  font-size: 28rpx;
  color: var(--lux-ink-900);
  box-sizing: border-box;
}

.form-input {
  width: 100%;
  height: 88rpx;
  background: var(--lux-surface-soft);
  border-radius: var(--lux-radius-sm);
  padding: 0 24rpx;
  font-size: 28rpx;
  color: var(--lux-ink-900);
  box-sizing: border-box;
}

.form-input--small {
  height: 64rpx;
  font-size: 26rpx;
  margin-top: 12rpx;
}

/* ========== 质检项区域 ========== */
.inspection-items-section {
  margin-top: 12rpx;
}

.section-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--lux-ink-900);
  margin-bottom: 20rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--lux-line-soft);
}

.qc-item-block {
  background: var(--lux-surface-soft);
  border-radius: 12rpx;
  padding: 20rpx;
  margin-bottom: 16rpx;
}

.qc-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10rpx;
}

.qc-item-name {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--lux-ink-900);
  flex: 1;
}

.qc-item-result-toggle {
  display: flex;
  gap: 8rpx;
}

.result-opt {
  font-size: 24rpx;
  padding: 8rpx 18rpx;
  border-radius: 8rpx;
  border: 2rpx solid transparent;
  background: var(--lux-surface);
  color: var(--lux-text-faint);
}

.result-opt--pass.result-opt--active {
  color: var(--lux-jade);
  background: var(--lux-jade-50);
  border-color: var(--lux-jade);
}

.result-opt--fail.result-opt--active {
  color: var(--lux-wine);
  background: var(--lux-wine-50);
  border-color: var(--lux-wine);
}

.qc-item-standard {
  font-size: 24rpx;
  color: var(--lux-text-faint);
  display: block;
}

.overall-result {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  padding: 20rpx 0 8rpx;
}

.overall-label {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--lux-ink-900);
}

.empty-items {
  padding: 40rpx 20rpx;
  text-align: center;
}

.empty-items-text {
  font-size: 26rpx;
  color: var(--lux-text-faint);
}

.overall-manual {
  margin-top: 8rpx;
}

.submit-hint {
  padding: 16rpx 20rpx;
  margin-top: 8rpx;
  border-radius: 12rpx;
  background: var(--lux-ember-50);
}

.submit-hint-text {
  font-size: 24rpx;
  color: var(--lux-ember-600);
}
.filter-heading { display: flex; align-items: center; justify-content: space-between; }
.filter-heading-title { font-size: 28rpx; font-weight: 600; color: var(--lux-ink-900); }
.filter-reset, .order-close, .order-search-clear, .empty-reset { margin: 0; padding: 12rpx 18rpx; background: transparent; color: var(--lux-gold-700); font-size: 26rpx; }
.filter-reset[disabled] { color: var(--lux-text-faint); opacity: .6; }
.work-order-trigger { display: flex; align-items: center; justify-content: space-between; width: 100%; min-height: 88rpx; padding: 16rpx 22rpx; margin: 0; border: 1rpx solid var(--lux-line); border-radius: var(--lux-radius-sm); background: var(--lux-surface-soft); text-align: left; }
.work-order-trigger-label { min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 28rpx; color: var(--lux-ink-900); }
.work-order-trigger-action { margin-left: 16rpx; flex-shrink: 0; font-size: 24rpx; color: var(--lux-gold-700); }
.filter-row { display: flex; align-items: center; gap: 14rpx; }
.filter-label { flex-shrink: 0; color: var(--lux-text); font-size: 24rpx; }
.filter-options { display: flex; flex: 1; min-width: 0; flex-wrap: wrap; gap: 10rpx; }
.filter-option { margin: 0; padding: 12rpx 18rpx; min-height: 72rpx; border-radius: 12rpx; background: var(--lux-porcelain-200); color: var(--lux-text); font-size: 26rpx; }
.filter-option--active { background: var(--lux-gold-50); color: var(--lux-gold-700); font-weight: 600; box-shadow: inset 0 0 0 2rpx var(--lux-gold-600); }
.filter-count { color: var(--lux-text); font-size: 24rpx; }
.order-filter-panel { max-height: 78vh; }
.order-search-wrapper { display: flex; align-items: center; margin: 24rpx 28rpx; border-radius: var(--lux-radius-sm); background: var(--lux-porcelain-100); }
.order-search { flex: 1; min-width: 0; height: 88rpx; padding: 0 22rpx; font-size: 28rpx; }
.order-options { height: 52vh; min-height: 0; }
.order-option { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; width: 100%; min-height: 100rpx; margin: 0; padding: 24rpx 32rpx; border-radius: 0; border-bottom: 1rpx solid var(--lux-line-soft); background: var(--lux-surface); text-align: left; color: var(--lux-ink-900); font-size: 28rpx; }
.order-option > text:first-child { flex: 1; min-width: 0; overflow-wrap: break-word; }
.order-option--active { background: var(--lux-gold-50); color: var(--lux-gold-700); font-weight: 600; }
.order-count { flex-shrink: 0; color: var(--lux-text); font-size: 24rpx; }
.order-empty { display: block; padding: 40rpx 28rpx; color: var(--lux-text); text-align: center; font-size: 26rpx; }
</style>
