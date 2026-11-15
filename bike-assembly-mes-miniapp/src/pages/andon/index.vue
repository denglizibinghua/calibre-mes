<template>
  <view class="andon-page">
    <!-- 关系筛选 -->
    <view class="filter-bar">
      <scroll-view scroll-x class="filter-scroll">
        <view
          v-for="f in relationFilters"
          :key="f.value"
          class="filter-tag"
          :class="{ 'filter-tag--active': activeRelation === f.value }"
          @click="activeRelation = f.value"
        >
          {{ f.label }}
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
    <view v-else-if="alertList.length === 0" class="state-box">
      <image class="empty-img" src="/static/icons/empty-box.svg" mode="aspectFit" />
      <text class="empty-title">暂无告警记录</text>
      <text class="empty-desc">{{ activeRelation === 'reported' ? '点击下方按钮上报告警' : '当前条件下没有数据' }}</text>
    </view>

    <!-- 告警列表 -->
    <view v-else class="alert-list">
      <view v-for="alert in alertList" :key="alert.alertId" class="alert-card">
        <view class="card-header">
          <view class="header-left">
            <text class="alert-no">{{ alert.alertNo }}</text>
            <text class="alert-type">{{ alert.alertType || '未分类' }}</text>
          </view>
          <text class="status-tag" :class="getAndonStatusClass(alert.status)">
            {{ getAndonStatusLabel(alert.status) }}
          </text>
        </view>

        <view class="card-body">
          <view class="info-row">
            <text class="info-label">告警等级</text>
            <text class="info-value" :class="getAlertLevelClass(alert.alertLevel)">
              {{ getAlertLevelLabel(alert.alertLevel) }}
            </text>
          </view>
          <view class="info-row">
            <text class="info-label">设备</text>
            <text class="info-value">{{ getEquipmentName(alert.equipmentId) }}</text>
          </view>
          <view class="info-row" v-if="alert.workOrderId">
            <text class="info-label">工单</text>
            <text class="info-value">{{ getWorkOrderNo(alert.workOrderId) }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">上报时间</text>
            <text class="info-value">{{ formatTime(alert.createTime) }}</text>
          </view>
          <view class="info-row" v-if="alert.handlerUserId">
            <text class="info-label">处理人</text>
            <text class="info-value">用户#{{ alert.handlerUserId }}</text>
          </view>
          <view class="info-row" v-if="alert.escalationStatus === '1'">
            <text class="info-label">升级状态</text>
            <text class="info-value info-value--escalated">已升级{{ alert.escalationRemark ? '：' + alert.escalationRemark : '' }}</text>
          </view>
        </view>

        <view
          v-if="(alert.status === '0' && hasPermission('app:andon:handle')) || (alert.status === '1' && hasPermission('app:andon:close')) || (alert.status !== '2' && alert.escalationStatus !== '1' && hasPermission('app:andon:handle'))"
          class="card-actions"
        >
          <button
            v-if="alert.status === '0' && hasPermission('app:andon:handle')"
            class="action-btn action-btn--accept"
            @click="handleAccept(alert)"
          >
            受理
          </button>
          <button
            v-if="alert.status === '1' && hasPermission('app:andon:close')"
            class="action-btn action-btn--close"
            @click="handleClose(alert)"
          >
            关闭
          </button>
          <button
            v-if="alert.status !== '2' && alert.escalationStatus !== '1' && hasPermission('app:andon:handle')"
            class="action-btn action-btn--escalate"
            @click="openEscalate(alert)"
          >
            升级
          </button>
        </view>
      </view>
    </view>

    <view class="bottom-placeholder" />

    <!-- 底部上报按钮 -->
    <view v-if="hasPermission('app:andon:create')" class="bottom-bar">
      <button class="create-btn" @click="openCreateForm">上报告警</button>
    </view>

    <!-- 升级告警弹窗 -->
    <view v-if="showEscalateForm" class="modal-mask" @click="showEscalateForm = false">
      <view class="modal-panel" @click.stop>
        <view class="modal-header">
          <text class="modal-title">升级告警</text>
          <text class="modal-close" @click="showEscalateForm = false">✕</text>
        </view>
        <view class="modal-body">
          <view class="form-item">
            <text class="form-label">升级备注（可选）</text>
            <textarea
              class="escalate-textarea"
              v-model="escalateForm.remark"
              placeholder="说明升级原因"
              maxlength="255"
            />
          </view>
        </view>
        <view class="modal-footer">
          <button class="modal-btn modal-btn--cancel" @click="showEscalateForm = false">取消</button>
          <button class="modal-btn modal-btn--confirm" :disabled="submitting" @click="submitEscalate">
            {{ submitting ? '提交中...' : '确认升级' }}
          </button>
        </view>
      </view>
    </view>

    <!-- 上报告警弹窗 -->
    <view v-if="showCreateForm" class="modal-mask" @click="showCreateForm = false">
      <view class="modal-panel" @click.stop>
        <view class="modal-header">
          <text class="modal-title">上报告警</text>
          <text class="modal-close" @click="showCreateForm = false">✕</text>
        </view>
        <view class="modal-body">
          <view class="form-item">
            <text class="form-label">告警类型 <text class="form-required">*</text></text>
            <picker class="form-picker" mode="selector" :range="alertTypeOptions" @change="onTypeChange">
              <view class="form-picker-value" :class="{ 'form-picker-value--empty': !createForm.alertType }">
                {{ createForm.alertType || '请选择告警类型' }}
              </view>
            </picker>
          </view>
          <view class="form-item">
            <text class="form-label">告警等级 <text class="form-required">*</text></text>
            <picker class="form-picker" mode="selector" :range="alertLevelOptions" range-key="label" @change="onLevelChange">
              <view class="form-picker-value">
                {{ getAlertLevelLabel(createForm.alertLevel) }}
              </view>
            </picker>
          </view>
          <view class="form-item">
            <text class="form-label">设备（选填）</text>
            <picker class="form-picker" mode="selector" :range="equipmentOptions" range-key="label" @change="onEquipmentChange">
              <view class="form-picker-value" :class="{ 'form-picker-value--empty': !createForm.equipmentId }">
                {{ selectedEquipmentLabel || '请选择设备' }}
              </view>
            </picker>
          </view>
          <view class="form-item">
            <text class="form-label">工单（选填）</text>
            <picker class="form-picker" mode="selector" :range="workOrderOptions" range-key="label" @change="onWorkOrderChange">
              <view class="form-picker-value" :class="{ 'form-picker-value--empty': !createForm.workOrderId }">
                {{ selectedWorkOrderLabel || '请选择工单' }}
              </view>
            </picker>
          </view>
        </view>
        <view class="modal-footer">
          <button class="modal-btn modal-btn--cancel" @click="showCreateForm = false">取消</button>
          <button class="modal-btn modal-btn--confirm" :disabled="submitting || !canSubmitCreate" @click="submitCreate">
            {{ submitting ? '提交中...' : '确认上报' }}
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { onShow, onHide, onUnload, onPullDownRefresh } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'
import { hasPermission } from '@/utils/permission'
import { getAndonListApi, createAndonApi, acceptAndonApi, closeAndonApi, escalateAndonApi } from '@/api/andon'
import { connectWs, closeWs, onWsMessage, onWsConnected } from '@/utils/websocket'
import { getBaseEquipmentListApi } from '@/api/reference'
import { getWorkOrderListApi } from '@/api/workOrder'
import {
  getAlertLevelLabel,
  getAlertLevelClass,
  getAndonStatusLabel,
  getAndonStatusClass,
  ALERT_TYPE_OPTIONS,
} from '@/types/andon'
import type { AndonAlertVO } from '@/types/andon'
import type { BaseEquipmentVO } from '@/types/reference'
import type { WorkOrderVO } from '@/types/workOrder'

const userStore = useUserStore()

const alertTypeOptions = ALERT_TYPE_OPTIONS

const loading = ref(false)
const loadError = ref('')
const submitting = ref(false)

const alertList = ref<AndonAlertVO[]>([])
const equipmentRefs = ref<BaseEquipmentVO[]>([])
const workOrderList = ref<WorkOrderVO[]>([])

const activeRelation = ref('reported')

const relationFilters = computed(() => {
  const arr = [
    { label: '我的上报', value: 'reported' },
    { label: '我处理的', value: 'handled' },
  ]
  if (hasPermission('app:andon:view-pending')) {
    arr.splice(1, 0, { label: '待处理', value: 'pending' })
  }
  if (hasPermission('app:andon:view-all')) {
    arr.push({ label: '全部', value: 'all' })
  }
  return arr
})

// 权限变化后自动切回合法 tab
watch(() => hasPermission('app:andon:view-all'), (canViewAll) => {
  if (!canViewAll && activeRelation.value === 'all') {
    activeRelation.value = 'reported'
  }
})
watch(() => hasPermission('app:andon:view-pending'), (canViewPending) => {
  if (!canViewPending && activeRelation.value === 'pending') {
    activeRelation.value = 'reported'
  }
})

const alertLevelOptions = [
  { label: '一般', value: '1' },
  { label: '紧急', value: '2' },
  { label: '严重', value: '3' },
]

const showCreateForm = ref(false)
const showEscalateForm = ref(false)
const escalateForm = reactive({ remark: '' })
const escalateTarget = ref<AndonAlertVO | null>(null)
const createForm = reactive({
  alertType: '',
  alertLevel: '1',
  equipmentId: null as number | null,
  workOrderId: null as number | null,
})

const canSubmitCreate = computed(() => createForm.alertType.trim() !== '')

const equipmentOptions = computed(() =>
  equipmentRefs.value.map((e) => ({
    label: `${e.equipmentName}（${e.equipmentCode}）`,
    value: e.equipmentId,
  })),
)
const selectedEquipmentLabel = computed(() => {
  const e = equipmentRefs.value.find((x) => x.equipmentId === createForm.equipmentId)
  return e ? e.equipmentName : ''
})
const workOrderOptions = computed(() =>
  workOrderList.value.map((w) => ({ label: w.workOrderNo, value: w.workOrderId })),
)
const selectedWorkOrderLabel = computed(() => {
  const w = workOrderList.value.find((x) => x.workOrderId === createForm.workOrderId)
  return w ? w.workOrderNo : ''
})

let pageActive = false
let fetchGeneration = 0
let unsubscribeMessage: (() => void) | undefined
let unsubscribeConnected: (() => void) | undefined

function stopRealtime(): void {
  const wasActive = pageActive
  pageActive = false
  fetchGeneration++
  unsubscribeMessage?.()
  unsubscribeConnected?.()
  unsubscribeMessage = unsubscribeConnected = undefined
  if (wasActive) closeWs()
}

function refreshVisiblePage(): void {
  if (pageActive && userStore.isLoggedIn && userStore.getAccessToken()) void fetchData()
}

onShow(() => {
  stopRealtime()
  if (!userStore.isLoggedIn || !userStore.getAccessToken()) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  if (!hasPermission('app:andon:view')) {
    uni.showToast({ title: '暂无访问权限', icon: 'none' })
    uni.navigateBack()
    return
  }
  pageActive = true
  void fetchData()
  unsubscribeMessage = onWsMessage('ANDON', refreshVisiblePage)
  unsubscribeConnected = onWsConnected(refreshVisiblePage)
  try {
    connectWs()
  } catch (err) {
    uni.showToast({ title: err instanceof Error ? err.message : '实时连接暂不可用', icon: 'none' })
  }
})

onHide(stopRealtime)
onUnload(stopRealtime)

onPullDownRefresh(() => {
  fetchData().finally(() => uni.stopPullDownRefresh())
})

watch(activeRelation, () => {
  fetchData()
})

function buildListParams(): { scope: string; relation?: string } {
  if (activeRelation.value === 'all') {
    return { scope: 'all' }
  }
  return { scope: 'mine', relation: activeRelation.value }
}

async function fetchData(): Promise<void> {
  if (!pageActive) return
  const requestId = ++fetchGeneration
  loading.value = true
  loadError.value = ''
  try {
    const [alerts, equipment] = await Promise.all([
      getAndonListApi(buildListParams()),
      getBaseEquipmentListApi(),
    ])
    if (!pageActive || requestId !== fetchGeneration) return
    alertList.value = alerts
    equipmentRefs.value = equipment
    // 工单列表仅对具备 app:work-order:view 的角色加载，避免无权限角色整页失败
    if (hasPermission('app:work-order:view')) {
      try {
        const orders = await getWorkOrderListApi('mine')
        if (!pageActive || requestId !== fetchGeneration) return
        workOrderList.value = orders
      } catch {
        if (!pageActive || requestId !== fetchGeneration) return
        workOrderList.value = []
      }
    } else {
      workOrderList.value = []
    }
  } catch (err) {
    if (!pageActive || requestId !== fetchGeneration) return
    console.error('[安灯告警] 数据加载失败:', err)
    loadError.value = '数据加载失败，请检查网络后重试'
  } finally {
    if (pageActive && requestId === fetchGeneration) loading.value = false
  }
}

function getEquipmentName(equipmentId: number | null): string {
  if (!equipmentId) return '—'
  const e = equipmentRefs.value.find((x) => x.equipmentId === equipmentId)
  return e ? e.equipmentName : `设备#${equipmentId}`
}

function getWorkOrderNo(workOrderId: number): string {
  const w = workOrderList.value.find((x) => x.workOrderId === workOrderId)
  return w ? w.workOrderNo : `工单#${workOrderId}`
}

function formatTime(s: string | null): string {
  if (!s) return '—'
  return s.substring(0, 16).replace('T', ' ')
}

function openCreateForm(): void {
  createForm.alertType = ''
  createForm.alertLevel = '1'
  createForm.equipmentId = null
  createForm.workOrderId = null
  showCreateForm.value = true
}

function onTypeChange(e: { detail: { value: number } }): void {
  createForm.alertType = ALERT_TYPE_OPTIONS[e.detail.value] ?? ''
}

function onLevelChange(e: { detail: { value: number } }): void {
  createForm.alertLevel = alertLevelOptions[e.detail.value]?.value ?? '1'
}

function onEquipmentChange(e: { detail: { value: number } }): void {
  const opt = equipmentOptions.value[e.detail.value]
  createForm.equipmentId = opt?.value ?? null
}

function onWorkOrderChange(e: { detail: { value: number } }): void {
  const opt = workOrderOptions.value[e.detail.value]
  createForm.workOrderId = opt?.value ?? null
}

async function submitCreate(): Promise<void> {
  if (!canSubmitCreate.value) {
    uni.showToast({ title: '请选择告警类型', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await createAndonApi({
      alertType: createForm.alertType,
      alertLevel: createForm.alertLevel,
      equipmentId: createForm.equipmentId ?? undefined,
      workOrderId: createForm.workOrderId ?? undefined,
    })
    uni.showToast({ title: '上报成功', icon: 'success' })
    showCreateForm.value = false
    activeRelation.value = 'reported'
    await fetchData()
  } catch (err) {
    console.error('[安灯告警] 上报告警失败:', err)
  } finally {
    submitting.value = false
  }
}

async function handleAccept(alert: AndonAlertVO): Promise<void> {
  uni.showModal({
    title: '受理告警',
    content: `确认受理告警「${alert.alertNo}」？`,
    success: async (res) => {
      if (!res.confirm) return
      submitting.value = true
      try {
        await acceptAndonApi(alert.alertId)
        uni.showToast({ title: '受理成功', icon: 'success' })
        await fetchData()
      } catch (err) {
        console.error('[安灯告警] 受理失败:', err)
      } finally {
        submitting.value = false
      }
    },
  })
}

async function handleClose(alert: AndonAlertVO): Promise<void> {
  uni.showModal({
    title: '关闭告警',
    content: `确认关闭告警「${alert.alertNo}」？关闭后不可恢复。`,
    success: async (res) => {
      if (!res.confirm) return
      submitting.value = true
      try {
        await closeAndonApi(alert.alertId)
        uni.showToast({ title: '已关闭', icon: 'success' })
        await fetchData()
      } catch (err) {
        console.error('[安灯告警] 关闭失败:', err)
      } finally {
        submitting.value = false
      }
    },
  })
}

function openEscalate(alert: AndonAlertVO): void {
  escalateTarget.value = alert
  escalateForm.remark = alert.escalationRemark || ''
  showEscalateForm.value = true
}

async function submitEscalate(): Promise<void> {
  const target = escalateTarget.value
  if (!target) return
  submitting.value = true
  try {
    await escalateAndonApi(target.alertId, escalateForm.remark.trim() || undefined)
    uni.showToast({ title: '升级成功', icon: 'success' })
    showEscalateForm.value = false
    await fetchData()
  } catch (err) {
    console.error('[安灯告警] 升级失败:', err)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.andon-page { min-height: 100vh; padding: 0 24rpx 140rpx; background: var(--lux-porcelain-100); }
.filter-bar { padding: 20rpx 0 4rpx; }
.filter-scroll { white-space: nowrap; }
.filter-tag { display: inline-block; padding: 12rpx 30rpx; margin-right: 16rpx; border: 1rpx solid var(--lux-line); border-radius: var(--lux-radius-xs); background: var(--lux-surface); color: var(--lux-text); font-size: 25rpx; }
.filter-tag--active { background: var(--lux-ink-900); color: var(--lux-porcelain-50); }
.state-box { display: flex; flex-direction: column; align-items: center; padding: 140rpx 40rpx; }
.state-text { color: var(--lux-text-faint); font-size: 26rpx; }
.state-text--error { color: var(--lux-wine); }
.state-retry-btn { margin-top: 24rpx; padding: 14rpx 40rpx; border: 1rpx solid var(--lux-gold-600); border-radius: var(--lux-radius-sm); background: var(--lux-surface); }
.retry-text { color: var(--lux-gold-700); font-size: 25rpx; }
.empty-img { width: 120rpx; height: 120rpx; opacity: .8; }
.empty-title { margin-top: 20rpx; color: var(--lux-ink-900); font-size: 30rpx; font-weight: 500; letter-spacing: 1rpx; }
.empty-desc { margin-top: 10rpx; color: var(--lux-text-faint); font-size: 24rpx; }
.alert-list { padding: 8rpx 0 20rpx; }
.alert-card { margin-top: 20rpx; padding: 26rpx 26rpx 10rpx; border: 1rpx solid var(--lux-line); border-radius: var(--lux-radius-md); background: var(--lux-surface); box-shadow: var(--lux-shadow-card); }
.card-header { display: flex; align-items: flex-start; justify-content: space-between; }
.header-left { display: flex; flex: 1; flex-direction: column; min-width: 0; }
.alert-no { color: var(--lux-ink-900); font-size: 29rpx; font-weight: 500; }
.alert-type { margin-top: 8rpx; color: var(--lux-text-faint); font-size: 24rpx; }
.status-tag { flex-shrink: 0; margin-left: 16rpx; padding: 6rpx 16rpx; border-radius: var(--lux-radius-xs); font-size: 22rpx; }
.status-tag.andon--pending { background: var(--lux-ember-50); color: var(--lux-ember-600); }
.status-tag.andon--processing { background: var(--lux-gold-50); color: var(--lux-gold-600); }
.status-tag.andon--closed { background: var(--lux-neutral-50); color: var(--lux-text-faint); }
.card-body { margin-top: 18rpx; padding-top: 6rpx; border-top: 1rpx solid var(--lux-line-soft); }
.info-row { display: flex; justify-content: space-between; padding: 10rpx 0; }
.info-label { color: var(--lux-text-faint); font-size: 24rpx; }
.info-value { color: var(--lux-text-soft); font-size: 24rpx; }
.info-value.level--info { color: var(--lux-gold-700); }
.info-value.level--warn { color: var(--lux-ember-600); }
.info-value.level--critical { color: var(--lux-wine); }
.card-actions { display: flex; justify-content: flex-end; gap: 16rpx; padding: 16rpx 0 6rpx; border-top: 1rpx solid var(--lux-line-soft); }
.action-btn { flex: 1; max-width: 200rpx; margin: 0; padding: 0; height: 64rpx; line-height: 64rpx; border-radius: var(--lux-radius-sm); font-size: 26rpx; }
.action-btn::after { border: none; }
.action-btn--accept { border: 1rpx solid var(--lux-gold-600); background: var(--lux-gold-gradient); color: #17130B; }
.action-btn--close { border: 1rpx solid var(--lux-ember-line); background: var(--lux-ember-50); color: var(--lux-ember-600); }
.action-btn--escalate { border: 1rpx solid var(--lux-wine-line); background: var(--lux-wine-50); color: var(--lux-wine); }
.info-value--escalated { color: var(--lux-wine); }
.escalate-textarea { width: 100%; min-height: 140rpx; padding: 20rpx 24rpx; box-sizing: border-box; border: 1rpx solid var(--lux-line); border-radius: var(--lux-radius-sm); background: var(--lux-surface-soft); color: var(--lux-text-soft); font-size: 26rpx; }
.bottom-bar { position: fixed; left: 0; right: 0; bottom: 0; z-index: 20; padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom)); background: var(--lux-surface); box-shadow: var(--lux-shadow-bar); }
.create-btn { height: 88rpx; line-height: 88rpx; border: 1rpx solid var(--lux-gold-600); border-radius: var(--lux-radius-sm); background: var(--lux-gold-gradient); color: #17130B; font-size: 30rpx; font-weight: 600; letter-spacing: 1rpx; }
.create-btn::after { border: none; }
.bottom-placeholder { height: 20rpx; }
.modal-mask { position: fixed; inset: 0; z-index: 100; display: flex; align-items: flex-end; background: rgba(7, 10, 15, .58); }
.modal-panel { width: 100%; padding: 30rpx 30rpx calc(30rpx + env(safe-area-inset-bottom)); border-radius: var(--lux-radius-md) var(--lux-radius-md) 0 0; background: var(--lux-surface); }
.modal-header { display: flex; align-items: center; justify-content: space-between; padding-bottom: 20rpx; }
.modal-title { color: var(--lux-ink-900); font-size: 32rpx; font-weight: 600; letter-spacing: 1rpx; }
.modal-close { color: var(--lux-text-faint); font-size: 34rpx; padding: 0 10rpx; }
.modal-body { max-height: 62vh; overflow-y: auto; }
.form-item { margin-top: 24rpx; }
.form-label { display: block; color: var(--lux-text-soft); font-size: 26rpx; margin-bottom: 12rpx; }
.form-required { color: var(--lux-wine); }
.form-picker { width: 100%; }
.form-picker-value { padding: 20rpx 24rpx; border: 1rpx solid var(--lux-line); border-radius: var(--lux-radius-sm); background: var(--lux-surface-soft); color: var(--lux-text-soft); font-size: 26rpx; }
.form-picker-value--empty { color: var(--lux-text-faint); }
.modal-footer { display: flex; gap: 20rpx; margin-top: 36rpx; }
.modal-btn { flex: 1; height: 84rpx; line-height: 84rpx; border-radius: var(--lux-radius-sm); font-size: 28rpx; margin: 0; }
.modal-btn::after { border: none; }
.modal-btn--cancel { background: var(--lux-porcelain-200); color: var(--lux-text); }
.modal-btn--confirm { border: 1rpx solid var(--lux-gold-600); background: var(--lux-gold-gradient); color: #17130B; }
.modal-btn--confirm[disabled] { background: var(--lux-porcelain-200); color: var(--lux-text-faint); }
</style>
