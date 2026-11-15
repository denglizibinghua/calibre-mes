<template>
  <view class="detail-page">
    <!-- 加载中 -->
    <view v-if="loading" class="state-box">
      <text class="state-text">加载中...</text>
    </view>

    <!-- 参数缺失 -->
    <view v-else-if="paramError" class="state-box">
      <text class="state-text state-text--error">{{ paramError }}</text>
      <view class="state-retry-btn" hover-class="state-retry-btn--hover" @click="goBack">
        <text class="retry-text">返回上一页</text>
      </view>
    </view>

    <!-- 加载失败 -->
    <view v-else-if="loadError" class="state-box">
      <text class="state-text state-text--error">{{ loadError }}</text>
      <view class="state-retry-btn" hover-class="state-retry-btn--hover" @click="fetchDetail">
        <text class="retry-text">重新加载</text>
      </view>
    </view>

    <!-- 内容 -->
    <template v-else-if="order">
      <!-- 工单基本信息 -->
      <view class="section">
        <view class="section-header">
          <text class="section-title">工单信息</text>
          <text class="status-tag" :class="'status-tag--' + getWorkOrderStatusClass(order.status)">
            {{ getWorkOrderStatusLabel(order.status) }}
          </text>
        </view>
        <view class="info-grid">
          <view class="info-item">
            <text class="info-label">工单编号</text>
            <text class="info-value">{{ order.workOrderNo }}</text>
          </view>
          <view class="info-item">
            <text class="info-label">订单号</text>
            <text class="info-value">{{ order.orderNo || '—' }}</text>
          </view>
          <view class="info-item">
            <text class="info-label">产品名称</text>
            <text class="info-value">{{ itemName }}</text>
          </view>
          <view class="info-item">
            <text class="info-label">计划数量</text>
            <text class="info-value">{{ order.planQty }} {{ order.unit }}</text>
          </view>
          <view class="info-item">
            <text class="info-label">计划开始</text>
            <text class="info-value">{{ formatDateTime(order.planStartTime) }}</text>
          </view>
          <view class="info-item">
            <text class="info-label">计划结束</text>
            <text class="info-value">{{ formatDateTime(order.planEndTime) }}</text>
          </view>
          <view class="info-item">
            <text class="info-label">创建时间</text>
            <text class="info-value">{{ formatDateTime(order.createTime) }}</text>
          </view>
        </view>
      </view>

      <view v-if="canViewProcesses" class="section">
        <view class="state-retry-btn" hover-class="state-retry-btn--hover" @click="enterWorkProcesses">
          <text class="retry-text">进入工序报工</text>
        </view>
      </view>

      <!-- 工序流转记录 -->
      <view v-if="canViewProcesses" class="section">
        <view class="section-header">
          <text class="section-title">工序流转记录</text>
          <text class="section-count">{{ processes.length }} 条</text>
        </view>

        <!-- 无流转记录 -->
        <view v-if="processes.length === 0" class="empty-process">
          <text class="empty-text">暂无工序流转记录</text>
        </view>

        <!-- 流转记录列表 -->
        <view v-else class="process-list">
          <view
            v-for="(proc, idx) in processes"
            :key="proc.processRecordId"
            class="process-card"
          >
            <view class="process-header">
              <view class="process-step">
                <view class="step-dot" :class="'step-dot--' + proc.status" />
                <text class="step-name">{{ getProcessName(proc.processId) }}</text>
              </view>
              <text class="process-status-text">{{ getWorkProcessStatusLabel(proc.status) }}</text>
            </view>
            <view class="process-body">
              <view class="process-info-row">
                <text class="process-label">设备</text>
                <text class="process-value">{{ getEquipmentName(proc.equipmentId) }}</text>
              </view>
              <view class="process-info-row">
                <text class="process-label">操作人</text>
                <text class="process-value">{{ proc.operator || '—' }}</text>
              </view>
              <view class="process-info-row">
                <text class="process-label">报工数量</text>
                <text class="process-value">{{ proc.reportQty }}</text>
              </view>
              <view class="process-info-row">
                <text class="process-label">质检结果</text>
                <text class="process-value" :class="'qc--' + proc.qcResult">
                  {{ getQcResultLabel(proc.qcResult) }}
                </text>
              </view>
              <view class="process-info-row">
                <text class="process-label">开始时间</text>
                <text class="process-value">{{ formatDateTime(proc.startTime) }}</text>
              </view>
              <view class="process-info-row">
                <text class="process-label">结束时间</text>
                <text class="process-value">{{ formatDateTime(proc.endTime) }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'
import { hasPermission } from '@/utils/permission'
import { getScanActionRoute, parsePositiveSafeId } from '@/utils/workOrderScan'
import { getWorkOrderDetailApi } from '@/api/workOrder'
import { getWorkProcessListApi } from '@/api/workProcess'
import { getBaseItemListApi, getBaseProcessListApi, getBaseEquipmentListApi } from '@/api/reference'
import { getWorkOrderStatusLabel, getWorkOrderStatusClass } from '@/types/workOrder'
import { getWorkProcessStatusLabel, getQcResultLabel } from '@/types/workProcess'
import type { WorkOrderVO } from '@/types/workOrder'
import type { WorkProcessVO } from '@/types/workProcess'
import type { BaseItemVO, BaseProcessVO, BaseEquipmentVO } from '@/types/reference'

const userStore = useUserStore()
const canViewProcesses = computed(() => hasPermission('app:work-process:view'))

const loading = ref(true)
const loadError = ref('')
const paramError = ref('')
const workOrderId = ref<number>(0)

const order = ref<WorkOrderVO | null>(null)
const processes = ref<WorkProcessVO[]>([])

const itemMap = ref<Record<number, string>>({})
const processMap = ref<Record<number, string>>({})
const equipmentMap = ref<Record<number, string>>({})

const itemName = computed(() => {
  if (!order.value) return '—'
  return itemMap.value[order.value.itemId] || `物料#${order.value.itemId}`
})

onLoad((options) => {
  if (!userStore.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }

  const id = options?.id
  if (!id) {
    paramError.value = '缺少工单 ID，请返回列表页重新进入'
    loading.value = false
    return
  }

  const parsedId = parsePositiveSafeId(id)
  if (parsedId === null) {
    paramError.value = '工单 ID 格式错误'
    loading.value = false
    return
  }

  workOrderId.value = parsedId
  fetchDetail()
})

async function fetchDetail(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const [orderData, allProcesses, items, procList, equipList] = await Promise.all([
      getWorkOrderDetailApi(workOrderId.value),
      canViewProcesses.value ? getWorkProcessListApi(hasPermission('app:work-process:view-all') ? 'all' : 'mine', workOrderId.value) : Promise.resolve([]),
      getBaseItemListApi(),
      canViewProcesses.value ? getBaseProcessListApi() : Promise.resolve([]),
      canViewProcesses.value ? getBaseEquipmentListApi() : Promise.resolve([]),
    ])

    order.value = orderData

    processes.value = allProcesses
      .filter((p) => p.workOrderId === workOrderId.value)
      .sort((a, b) => {
        if (a.createTime && b.createTime) {
          return a.createTime.localeCompare(b.createTime)
        }
        return 0
      })

    const im: Record<number, string> = {}
    items.forEach((item: BaseItemVO) => {
      im[item.itemId] = item.itemName
    })
    itemMap.value = im

    const pm: Record<number, string> = {}
    procList.forEach((p: BaseProcessVO) => {
      pm[p.processId] = p.processName
    })
    processMap.value = pm

    const em: Record<number, string> = {}
    equipList.forEach((e: BaseEquipmentVO) => {
      em[e.equipmentId] = e.equipmentName
    })
    equipmentMap.value = em
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '数据加载失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

function getProcessName(processId: number): string {
  return processMap.value[processId] || `工序#${processId}`
}

function getEquipmentName(equipmentId: number | null): string {
  if (equipmentId === null) return '—'
  return equipmentMap.value[equipmentId] || `设备#${equipmentId}`
}

function formatDateTime(s: string | null): string {
  if (!s) return '—'
  return s.substring(0, 16).replace('T', ' ')
}

function enterWorkProcesses(): void {
  if (!canViewProcesses.value) return
  const url = getScanActionRoute('WORK_PROCESS', workOrderId.value)
  if (url) uni.navigateTo({ url })
}

function goBack(): void {
  uni.navigateBack()
}
</script>

<style scoped>
.detail-page {
  min-height: 100vh;
  background: var(--lux-porcelain-100);
  padding-bottom: env(safe-area-inset-bottom);
}

/* ========== 通用状态 ========== */
.state-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 200rpx 40rpx;
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

/* ========== 区块 ========== */
.section {
  margin: 16rpx 28rpx;
  background: var(--lux-surface);
  border-radius: var(--lux-radius-md);
  padding: 28rpx;
  border: 1rpx solid var(--lux-line);
  box-shadow: var(--lux-shadow-card);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
  padding-bottom: 20rpx;
  border-bottom: 1rpx solid var(--lux-line-soft);
}

.section-title {
  font-size: 30rpx;
  font-weight: 500;
  letter-spacing: 1rpx;
  color: var(--lux-ink-900);
}

.section-count {
  font-size: 24rpx;
  color: var(--lux-text-faint);
}

/* ========== 状态标签 ========== */
.status-tag {
  font-size: 22rpx;
  padding: 6rpx 14rpx;
  border-radius: var(--lux-radius-xs);
  font-weight: 500;
}

.status-tag--pending {
  color: var(--lux-ember);
  background: var(--lux-ember-50);
}

.status-tag--in-progress {
  color: var(--lux-gold-600);
  background: var(--lux-gold-50);
}

.status-tag--completed {
  color: var(--lux-jade);
  background: var(--lux-jade-50);
}

.status-tag--closed {
  color: var(--lux-text-faint);
  background: var(--lux-porcelain-200);
}

.status-tag--released {
  color: var(--lux-ember);
  background: var(--lux-ember-50);
}

.status-tag--paused {
  color: var(--lux-ember);
  background: var(--lux-ember-50);
}

.status-tag--cancelled {
  color: var(--lux-wine);
  background: var(--lux-wine-50);
}

.status-tag--default {
  color: var(--lux-text);
  background: var(--lux-porcelain-200);
}

/* ========== 信息网格 ========== */
.info-grid {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18rpx 0;
  border-bottom: 1rpx solid var(--lux-line-soft);
}

.info-item:last-child {
  border-bottom: none;
}

.info-label {
  font-size: 26rpx;
  color: var(--lux-text-faint);
  flex-shrink: 0;
}

.info-value {
  font-size: 26rpx;
  color: var(--lux-text);
  text-align: right;
  max-width: 440rpx;
  word-break: break-all;
}

/* ========== 工序流转 ========== */
.empty-process {
  padding: 48rpx 0;
  text-align: center;
}

.empty-text {
  font-size: 26rpx;
  color: var(--lux-text-faint);
}

.process-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.process-card {
  background: var(--lux-surface-soft);
  border-radius: var(--lux-radius-md);
  padding: 24rpx;
  border-left: 4rpx solid var(--lux-gold-600);
}

.process-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}

.process-step {
  display: flex;
  align-items: center;
}

.step-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  margin-right: 12rpx;
  background: var(--lux-text-faint);
}

.step-dot--0 { background: var(--lux-text-faint); }
.step-dot--1 { background: var(--lux-gold-600); }
.step-dot--2 { background: var(--lux-jade); }
.step-dot--3 { background: var(--lux-ember); }
.step-dot--4 { background: var(--lux-jade); }
.step-dot--5 { background: var(--lux-wine); }
.step-dot--6 { background: var(--lux-ember); }
.step-dot--7 { background: var(--lux-text-faint); }
.step-dot--8 { background: var(--lux-text-faint); }
.step-dot--9 { background: var(--lux-gold-600); }

.step-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--lux-ink-900);
}

.process-status-text {
  font-size: 24rpx;
  color: var(--lux-text-faint);
}

.process-body {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.process-info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.process-label {
  font-size: 24rpx;
  color: var(--lux-text-faint);
}

.process-value {
  font-size: 24rpx;
  color: var(--lux-text);
}

.qc--0 { color: var(--lux-ember); font-weight: 500; }
.qc--1 { color: var(--lux-jade); font-weight: 500; }
.qc--2 { color: var(--lux-wine); font-weight: 500; }
.qc--3 { color: var(--lux-ember); font-weight: 500; }
</style>
