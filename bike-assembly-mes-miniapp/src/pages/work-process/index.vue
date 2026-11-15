<template>
  <view class="wp-page">
    <!-- 顶部摘要 -->
    <view class="summary-bar">
      <view class="summary-item">
        <text class="summary-num">{{ summary.total }}</text>
        <text class="summary-label">全部记录</text>
      </view>
      <view class="summary-item">
        <text class="summary-num summary-num--active">{{ summary.inProgress }}</text>
        <text class="summary-label">进行中</text>
      </view>
      <view class="summary-item">
        <text class="summary-num summary-num--done">{{ summary.completed }}</text>
        <text class="summary-label">已完成</text>
      </view>
    </view>

    <!-- 数据范围筛选 -->
    <view v-if="hasAllDataScope()" class="filter-bar scope-bar">
      <scroll-view scroll-x class="filter-scroll">
        <view
          v-for="s in scopeFilters"
          :key="s.value"
          class="filter-tag"
          :class="{ 'filter-tag--active': activeScope === s.value }"
          @click="activeScope = s.value"
        >
          {{ s.label }}
        </view>
      </scroll-view>
    </view>

    <view v-if="scanWorkOrderId" class="filter-bar">
      <text class="filter-tag filter-tag--active">扫码定位：{{ selectedWorkOrderLabel }}</text>
      <text class="filter-tag" @click="clearScanLocation">清除扫码定位</text>
    </view>

    <!-- 筛选栏 -->
    <view class="filter-bar">
      <picker
        class="filter-picker"
        :disabled="!!scanWorkOrderId"
        mode="selector"
        :range="workOrderOptions"
        range-key="label"
        @change="onWorkOrderFilter"
      >
        <view class="filter-tag" :class="{ 'filter-tag--active': filterWorkOrderId }">
          {{ filterWorkOrderId ? selectedWorkOrderLabel : '全部工单 ▾' }}
        </view>
      </picker>
      <scroll-view scroll-x class="status-scroll">
        <view
          v-for="s in statusFilters"
          :key="s.value"
          class="filter-tag"
          :class="{ 'filter-tag--active': filterStatus === s.value }"
          @click="filterStatus = s.value"
        >
          {{ s.label }}
        </view>
      </scroll-view>
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
      <text class="empty-title">暂无工序报工记录</text>
      <text class="empty-desc">{{ scanWorkOrderId ? '当前工单暂无符合条件的工序记录，可调整状态筛选或清除扫码定位' : '请选择工单或调整状态筛选' }}</text>
    </view>

    <!-- 工序记录列表 -->
    <view v-else class="process-list">
      <view
        v-for="proc in filteredList"
        :key="proc.processRecordId"
        class="process-card"
      >
        <view class="card-header">
          <view class="header-left">
            <text class="process-name">{{ getProcessName(proc.processId) }}</text>
            <text class="wo-no">{{ getWorkOrderNo(proc.workOrderId) }}</text>
          </view>
          <text class="status-tag" :class="'status--' + proc.status">
            {{ getWorkProcessStatusLabel(proc.status) }}
          </text>
        </view>

        <view class="card-body">
          <view class="info-row">
            <text class="info-label">操作人</text>
            <text class="info-value">{{ proc.operator || '—' }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">报工数量</text>
            <text class="info-value">{{ proc.reportQty }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">质检结果</text>
            <text class="info-value" :class="'qc--' + proc.qcResult">{{ getQcResultLabel(proc.qcResult) }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">设备</text>
            <text class="info-value">{{ getEquipmentName(proc.equipmentId) }}</text>
          </view>
          <view class="info-row" v-if="proc.startTime">
            <text class="info-label">开始时间</text>
            <text class="info-value">{{ formatTime(proc.startTime) }}</text>
          </view>
          <view class="info-row" v-if="proc.endTime">
            <text class="info-label">结束时间</text>
            <text class="info-value">{{ formatTime(proc.endTime) }}</text>
          </view>
        </view>

        <view class="card-actions" v-if="proc.status !== '2' && proc.status !== '7' && proc.status !== '8'">
          <!-- 可开工 → 开始工作 -->
          <button
            v-if="proc.status === '9' && hasPermission('app:work-process:start')"
            class="action-btn action-btn--start"
            @click="handleStartWork(proc)"
          >
            开始工作
          </button>
          <!-- 进行中 → 报工（后端自动判断是否需要质检） -->
          <template v-if="proc.status === '1'">
            <button
              v-if="hasPermission('app:work-process:report')"
              class="action-btn action-btn--qc"
              @click="openCompleteForm(proc, 'qc')"
            >
              报工
            </button>
          </template>
          <!-- 检验不合格 → 发起返工 -->
          <button
            v-if="proc.status === '5'"
            class="action-btn action-btn--rework"
            @click="handleReworkStart(proc)"
          >
            发起返工
          </button>
          <!-- 返工中 → 返工完成 -->
          <button
            v-if="proc.status === '6'"
            class="action-btn action-btn--rework"
            @click="openReworkCompleteForm(proc)"
          >
            返工完成(报工)
          </button>
        </view>
      </view>
    </view>

    <!-- ========== 完成报工弹窗 ========== -->
    <view v-if="showCompleteForm" class="modal-mask" @click="showCompleteForm = false">
      <view class="modal-panel" @click.stop>
        <view class="modal-header">
          <text class="modal-title">{{ completeMode === 'qc' ? '报工' : completeMode === 'rework' ? '返工完成' : '报工' }}</text>
          <text class="modal-close" @click="showCompleteForm = false">✕</text>
        </view>
        <view class="modal-body">
          <view class="form-item">
            <text class="form-label">
              {{ getProcessName(completingProc?.processId || 0) }} · {{ getWorkOrderNo(completingProc?.workOrderId || 0) }}
            </text>
          </view>
          <view v-if="completeMode === 'rework'" class="form-item">
            <text class="form-desc">返工完成后将重新送检，无需填写报工数量。</text>
          </view>
          <view v-if="completeMode !== 'rework'" class="form-item">
            <text class="form-label">报工数量 <text class="form-required">*</text></text>
            <input
              class="form-input"
              v-model="completeForm.reportQty"
              type="digit"
              placeholder="请输入报工数量"
              placeholder-style="color: #6F6A5F"
            />
          </view>
        </view>
        <view class="modal-footer">
          <button class="modal-btn modal-btn--cancel" @click="showCompleteForm = false">取消</button>
          <button class="modal-btn modal-btn--confirm" :disabled="submitting" @click="submitComplete">
            {{ submitting ? '提交中...' : '确认' }}
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { onLoad, onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'
import { parsePositiveSafeId } from '@/utils/workOrderScan'
import { hasPermission, hasAllDataScope } from '@/utils/permission'
import {
  getWorkProcessListApi,
  startWorkProcessApi, reportWorkProcessApi,
  reworkStartWorkProcessApi, reworkCompleteWorkProcessApi,
} from '@/api/workProcess'
import { getWorkOrderListApi } from '@/api/workOrder'
import { getBaseProcessListApi, getBaseEquipmentListApi } from '@/api/reference'
import { getWorkProcessStatusLabel, getQcResultLabel } from '@/types/workProcess'
import type { WorkProcessVO } from '@/types/workProcess'
import type { WorkOrderVO } from '@/types/workOrder'
import type { BaseProcessVO, BaseEquipmentVO } from '@/types/reference'

const userStore = useUserStore()

const loading = ref(false)
const loadError = ref('')
const paramError = ref('')
const scanWorkOrderId = ref<number | null>(null)
const submitting = ref(false)
let fetchGeneration = 0

const processList = ref<WorkProcessVO[]>([])
const workOrderList = ref<WorkOrderVO[]>([])
const processRefs = ref<BaseProcessVO[]>([])
const equipmentRefs = ref<BaseEquipmentVO[]>([])

const filterWorkOrderId = ref<number>(0)
const filterStatus = ref('')
const activeScope = ref('mine')

// 只有拥有 view-all 权限时才显示"全部记录"选项
const scopeFilters = computed(() => {
  const filters = [{ label: '我的记录', value: 'mine' }]
  if (hasPermission('app:work-process:view-all')) {
    filters.push({ label: '全部记录', value: 'all' })
  }
  return filters
})

// 权限变化后如果当前为 all 但失去权限，自动切回 mine
watch(() => hasPermission('app:work-process:view-all'), (canViewAll) => {
  if (!canViewAll && activeScope.value === 'all') {
    activeScope.value = 'mine'
  }
})

const statusFilters = [
  { label: '全部', value: '' },
  { label: '可开工', value: '9' },
  { label: '进行中', value: '1' },
  { label: '待检', value: '3' },
  { label: '已完成', value: '2' },
  { label: '待返工', value: '5' },
  { label: '返工中', value: '6' },
]

const showCompleteForm = ref(false)
const completingProc = ref<WorkProcessVO | null>(null)

const completeForm = reactive({
  reportQty: '',
})

const summary = computed(() => {
  const total = processList.value.length
  const inProgress = processList.value.filter((p) => p.status === '1').length
  const completed = processList.value.filter((p) => p.status === '2').length
  return { total, inProgress, completed }
})

const filteredList = computed(() => {
  let list = processList.value
  if (filterWorkOrderId.value) {
    list = list.filter((p) => p.workOrderId === filterWorkOrderId.value)
  }
  if (filterStatus.value !== '') {
    list = list.filter((p) => p.status === filterStatus.value)
  }
  return [...list].sort((a, b) => {
    if (a.createTime && b.createTime) return b.createTime.localeCompare(a.createTime)
    return 0
  })
})

const workOrderOptions = computed(() =>
  [{ label: '全部工单', value: 0 }, ...workOrderList.value.map((wo) => ({
    label: `${wo.workOrderNo}`,
    value: wo.workOrderId,
  }))]
)

const selectedWorkOrderLabel = computed(() => {
  const wo = workOrderList.value.find((w) => w.workOrderId === filterWorkOrderId.value)
  return wo ? wo.workOrderNo : filterWorkOrderId.value ? `工单 #${filterWorkOrderId.value}` : ''
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
  filterWorkOrderId.value = id
  activeScope.value = hasPermission('app:work-process:view-all') ? 'all' : 'mine'
})

onShow(() => {
  if (!userStore.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  if (!hasPermission('app:work-process:view')) {
    uni.showToast({ title: '暂无访问权限', icon: 'none' })
    uni.navigateBack()
    return
  }
  fetchData()
})

onPullDownRefresh(() => {
  fetchData().finally(() => uni.stopPullDownRefresh())
})

// scope 切换时立即重新加载数据（mine ↔ all）
watch(activeScope, () => {
  fetchData()
})

async function fetchData(): Promise<void> {
  const generation = ++fetchGeneration
  if (paramError.value || !userStore.isLoggedIn || !hasPermission('app:work-process:view')) return
  const requestWorkOrderId = scanWorkOrderId.value ?? undefined
  const processScope = activeScope.value
  const canViewOrders = hasPermission('app:work-order:view')
  const orderScope = hasPermission('app:work-order:view-all') ? 'all' : 'mine'
  loading.value = true
  loadError.value = ''
  try {
    const [processes, orders, processes2, equipment] = await Promise.all([
      getWorkProcessListApi(processScope, requestWorkOrderId),
      canViewOrders
        ? getWorkOrderListApi(orderScope).catch(() => [])
        : Promise.resolve([]),
      getBaseProcessListApi(),
      getBaseEquipmentListApi(),
    ])
    if (generation !== fetchGeneration) return
    processList.value = processes
    workOrderList.value = orders
    processRefs.value = processes2
    equipmentRefs.value = equipment
  } catch (error) {
    if (generation === fetchGeneration) {
      loadError.value = error instanceof Error ? error.message : '数据加载失败，请检查网络后重试'
    }
  } finally {
    if (generation === fetchGeneration) loading.value = false
  }
}

function getWorkOrderNo(workOrderId: number): string {
  const wo = workOrderList.value.find((w) => w.workOrderId === workOrderId)
  return wo ? wo.workOrderNo : `工单#${workOrderId}`
}

function getProcessName(processId: number): string {
  const p = processRefs.value.find((p) => p.processId === processId)
  return p ? p.processName : `工序#${processId}`
}

function getEquipmentName(equipmentId: number | null): string {
  if (!equipmentId) return '—'
  const e = equipmentRefs.value.find((e) => e.equipmentId === equipmentId)
  return e ? e.equipmentName : `设备#${equipmentId}`
}

function formatTime(s: string | null): string {
  if (!s) return '—'
  return s.substring(0, 16).replace('T', ' ')
}

function clearScanLocation(): void {
  scanWorkOrderId.value = null
  filterWorkOrderId.value = 0
  fetchData()
}

function onWorkOrderFilter(e: { detail: { value: number } }): void {
  const idx = e.detail.value
  filterWorkOrderId.value = idx >= 0 ? workOrderOptions.value[idx]?.value ?? 0 : 0
}

async function handleStartWork(proc: WorkProcessVO): Promise<void> {
  uni.showModal({
    title: '开始工作',
    content: `确认开始工序「${getProcessName(proc.processId)}」？`,
    success: async (res) => {
      if (!res.confirm) return
      submitting.value = true
      try {
        await startWorkProcessApi(proc.processRecordId)
        uni.showToast({ title: '已开始', icon: 'success' })
        await fetchData()
      } catch {
        // error handled by request layer
      } finally {
        submitting.value = false
      }
    },
  })
}

/** 完成模式：'qc'=报工送检, 'done'=报工完成, 'rework'=返工完成 */
const completeMode = ref<'qc' | 'done' | 'rework'>('done')

function openCompleteForm(proc: WorkProcessVO, mode: 'qc' | 'done' = 'done'): void {
  completingProc.value = proc
  completeForm.reportQty = ''
  completeMode.value = mode
  showCompleteForm.value = true
}

async function handleReworkStart(proc: WorkProcessVO): Promise<void> {
  uni.showModal({
    title: '发起返工',
    content: `确认对工序「${getProcessName(proc.processId)}」发起返工？`,
    success: async (res) => {
      if (!res.confirm) return
      submitting.value = true
      try {
        await reworkStartWorkProcessApi(proc.processRecordId)
        uni.showToast({ title: '返工已发起', icon: 'success' })
        await fetchData()
      } catch {
        // error handled by request layer
      } finally {
        submitting.value = false
      }
    },
  })
}

function openReworkCompleteForm(proc: WorkProcessVO): void {
  completingProc.value = proc
  completeForm.reportQty = ''
  completeMode.value = 'rework'
  showCompleteForm.value = true
}

async function submitComplete(): Promise<void> {
  if (completeMode.value !== 'rework' && (!completeForm.reportQty || Number(completeForm.reportQty) <= 0)) {
    uni.showToast({ title: '请输入有效报工数量', icon: 'none' })
    return
  }
  if (!completingProc.value) return

  submitting.value = true
  try {
    const reportQty = Number(completeForm.reportQty)
    if (completeMode.value === 'rework') {
      // 返工完成，回到待检状态（不传报工数量）
      await reworkCompleteWorkProcessApi(completingProc.value.processRecordId)
      uni.showToast({ title: '返工完成，待复检', icon: 'success' })
    } else {
      // 统一报工（后端自动判断是否需要质检）
      await reportWorkProcessApi(completingProc.value.processRecordId, reportQty)
      uni.showToast({ title: '已报工送检', icon: 'success' })
    }
    showCompleteForm.value = false
    completingProc.value = null
    await fetchData()
  } catch {
    // error handled by request layer
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.wp-page {
  min-height: 100vh;
  background: var(--lux-porcelain-100);
  padding-bottom: 120rpx;
}

/* ========== 摘要栏 ========== */
.summary-bar {
  display: flex;
  background: var(--lux-surface);
  padding: 24rpx 28rpx;
  border-bottom: 1rpx solid var(--lux-line-soft);
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
  font-family: var(--lux-font-data);
  font-variant-numeric: tabular-nums;
  color: var(--lux-ink-900);
}

.summary-num--active { color: var(--lux-gold-600); }
.summary-num--done { color: var(--lux-jade); }

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
  align-items: center;
  gap: 12rpx;
}

.filter-picker {
  flex-shrink: 0;
}

.status-scroll {
  display: flex;
  white-space: nowrap;
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
  margin-right: 12rpx;
  flex-shrink: 0;
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

/* ========== 工序列表 ========== */
.process-list {
  padding: 16rpx 28rpx;
}

.process-card {
  background: var(--lux-surface);
  border-radius: var(--lux-radius-md);
  padding: 28rpx;
  margin-bottom: 16rpx;
  border: 1rpx solid var(--lux-line);
  box-shadow: var(--lux-shadow-card);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20rpx;
}

.header-left {
  display: flex;
  flex-direction: column;
}

.process-name {
  font-size: 30rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
  color: var(--lux-ink-900);
}

.wo-no {
  font-size: 24rpx;
  color: var(--lux-text-faint);
  margin-top: 6rpx;
}

/* 状态标签 */
.status-tag {
  font-size: 22rpx;
  padding: 6rpx 14rpx;
  border-radius: var(--lux-radius-xs);
  font-weight: 500;
  flex-shrink: 0;
}

.status--0 { color: var(--lux-text-faint); background: var(--lux-porcelain-200); }
.status--1 { color: var(--lux-gold-600); background: var(--lux-gold-50); }
.status--2 { color: var(--lux-jade); background: var(--lux-jade-50); }
.status--3 { color: var(--lux-ember); background: var(--lux-ember-50); }
.status--4 { color: var(--lux-jade); background: var(--lux-jade-50); }
.status--5 { color: var(--lux-wine); background: var(--lux-wine-50); }
.status--6 { color: var(--lux-ember); background: var(--lux-ember-50); }
.status--7 { color: var(--lux-text-faint); background: var(--lux-porcelain-200); }
.status--8 { color: var(--lux-text-faint); background: var(--lux-porcelain-200); }
.status--9 { color: var(--lux-gold-600); background: var(--lux-gold-50); }

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

.qc--0 { color: var(--lux-ember); font-weight: 500; }
.qc--1 { color: var(--lux-jade); font-weight: 500; }
.qc--2 { color: var(--lux-wine); font-weight: 500; }
.qc--3 { color: var(--lux-ember); font-weight: 500; }

/* 操作按钮 */
.card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 16rpx;
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid var(--lux-line-soft);
}

.action-btn {
  height: 60rpx;
  line-height: 60rpx;
  padding: 0 28rpx;
  font-size: 26rpx;
  border-radius: var(--lux-radius-sm);
  border: none;
  font-weight: 500;
}

.action-btn--start {
  border: 1rpx solid var(--lux-gold-600);
  color: #17130B;
  background: var(--lux-gold-gradient);
}

.action-btn--complete {
  border: 1rpx solid var(--lux-jade);
  color: var(--lux-porcelain-50);
  background: var(--lux-jade);
}

.action-btn:active {
  opacity: 0.85;
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
  box-shadow: var(--lux-shadow-bar);
}

.create-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: var(--lux-gold-gradient);
  color: #17130B;
  font-size: 30rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
  border-radius: var(--lux-radius-sm);
  border: 1rpx solid var(--lux-gold-600);
}

.create-btn:active { opacity: 0.85; }

/* ========== 弹窗 ========== */
.modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(7, 10, 15, 0.58);
  display: flex;
  align-items: flex-end;
  z-index: 100;
}

.modal-panel {
  width: 100%;
  background: var(--lux-surface);
  border-radius: var(--lux-radius-md) var(--lux-radius-md) 0 0;
  max-height: 80vh;
  overflow-y: auto;
  padding-bottom: env(safe-area-inset-bottom);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 32rpx;
  border-bottom: 1rpx solid var(--lux-line-soft);
}

.modal-title {
  font-size: 32rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
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
}

.modal-footer {
  display: flex;
  gap: 16rpx;
  padding: 24rpx 32rpx;
  border-top: 1rpx solid var(--lux-line-soft);
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
  border: 1rpx solid var(--lux-gold-600);
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
  font-weight: 500;
  color: var(--lux-ink-900);
  margin-bottom: 14rpx;
}

.form-required {
  color: var(--lux-wine);
}

.form-desc {
  font-size: 24rpx;
  color: var(--lux-text-faint);
  line-height: 1.6;
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

.qc-options {
  display: flex;
  gap: 16rpx;
}

.qc-option {
  flex: 1;
  height: 64rpx;
  line-height: 64rpx;
  text-align: center;
  font-size: 26rpx;
  color: var(--lux-text);
  background: var(--lux-porcelain-200);
  border-radius: var(--lux-radius-sm);
  border: 2rpx solid transparent;
  transition: all 0.2s ease;
}

.qc-option--active {
  color: var(--lux-gold-600);
  background: var(--lux-gold-50);
  border-color: var(--lux-gold-600);
  font-weight: 500;
}
</style>
