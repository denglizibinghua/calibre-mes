<template>
  <view class="repair-page">
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
    <view v-else-if="repairList.length === 0" class="state-box">
      <image class="empty-img" src="/static/icons/empty-box.svg" mode="aspectFit" />
      <text class="empty-title">暂无维修单</text>
      <text class="empty-desc">{{ activeRelation === 'reported' ? '点击下方按钮提交设备报修' : '当前条件下没有数据' }}</text>
    </view>

    <!-- 维修单列表 -->
    <view v-else class="repair-list">
      <view v-for="item in repairList" :key="item.repairId" class="repair-card">
        <view class="card-header">
          <view class="header-left">
            <text class="equip-name">{{ getEquipmentName(item.equipmentId) }}</text>
            <text class="repair-no">维修单 #{{ item.repairId }}</text>
          </view>
          <text class="status-tag" :class="getRepairStatusClass(item.status)">
            {{ getRepairStatusLabel(item.status) }}
          </text>
        </view>

        <view class="fault-box">
          <text class="fault-text">{{ item.faultDesc }}</text>
        </view>

        <view class="card-body">
          <view class="info-row">
            <text class="info-label">报修人</text>
            <text class="info-value">{{ item.reportUser || '—' }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">报修时间</text>
            <text class="info-value">{{ formatTime(item.reportTime) }}</text>
          </view>
          <view class="info-row" v-if="item.repairUser">
            <text class="info-label">维修人</text>
            <text class="info-value">{{ item.repairUser }}</text>
          </view>
          <view class="info-row" v-if="item.resultDesc">
            <text class="info-label">维修结果</text>
            <text class="info-value">{{ item.resultDesc }}</text>
          </view>
          <view class="info-row" v-if="item.finishTime">
            <text class="info-label">完成时间</text>
            <text class="info-value">{{ formatTime(item.finishTime) }}</text>
          </view>
        </view>

        <view
          v-if="(item.status === '0' && hasPermission('app:repair:accept')) || (item.status === '1' && hasPermission('app:repair:complete'))"
          class="card-actions"
        >
          <button
            v-if="item.status === '0' && hasPermission('app:repair:accept')"
            class="action-btn action-btn--accept"
            @click="handleAccept(item)"
          >
            接单维修
          </button>
          <button
            v-if="item.status === '1' && hasPermission('app:repair:complete')"
            class="action-btn action-btn--complete"
            @click="openCompleteForm(item)"
          >
            完成维修
          </button>
        </view>
      </view>
    </view>

    <view class="bottom-placeholder" />

    <!-- 底部报修按钮 -->
    <view v-if="hasPermission('app:repair:create')" class="bottom-bar">
      <button class="create-btn" @click="openCreateForm">提交设备报修</button>
    </view>

    <!-- 提交报修弹窗 -->
    <view v-if="showCreateForm" class="modal-mask" @click="showCreateForm = false">
      <view class="modal-panel" @click.stop>
        <view class="modal-header">
          <text class="modal-title">提交设备报修</text>
          <text class="modal-close" @click="showCreateForm = false">✕</text>
        </view>
        <view class="modal-body">
          <view class="form-item">
            <text class="form-label">设备 <text class="form-required">*</text></text>
            <picker class="form-picker" mode="selector" :range="equipmentOptions" range-key="label" @change="onEquipmentChange">
              <view class="form-picker-value" :class="{ 'form-picker-value--empty': !createForm.equipmentId }">
                {{ selectedEquipmentLabel || '请选择设备' }}
              </view>
            </picker>
          </view>
          <view class="form-item">
            <text class="form-label">故障描述 <text class="form-required">*</text></text>
            <textarea
              class="form-textarea"
              v-model="createForm.faultDesc"
              placeholder="请描述设备故障情况"
              placeholder-style="color: #a7b1c2"
              :maxlength="255"
            />
          </view>
        </view>
        <view class="modal-footer">
          <button class="modal-btn modal-btn--cancel" @click="showCreateForm = false">取消</button>
          <button class="modal-btn modal-btn--confirm" :disabled="submitting || !canSubmitCreate" @click="submitCreate">
            {{ submitting ? '提交中...' : '确认报修' }}
          </button>
        </view>
      </view>
    </view>

    <!-- 完成维修弹窗 -->
    <view v-if="showCompleteForm" class="modal-mask" @click="showCompleteForm = false">
      <view class="modal-panel" @click.stop>
        <view class="modal-header">
          <text class="modal-title">完成维修</text>
          <text class="modal-close" @click="showCompleteForm = false">✕</text>
        </view>
        <view class="modal-body">
          <view class="form-item">
            <text class="form-label">维修结果 <text class="form-required">*</text></text>
            <textarea
              class="form-textarea"
              v-model="completeForm.resultDesc"
              placeholder="请填写维修结果/处理说明"
              placeholder-style="color: #a7b1c2"
              :maxlength="255"
            />
          </view>
        </view>
        <view class="modal-footer">
          <button class="modal-btn modal-btn--cancel" @click="showCompleteForm = false">取消</button>
          <button class="modal-btn modal-btn--confirm" :disabled="submitting || !canSubmitComplete" @click="submitComplete">
            {{ submitting ? '提交中...' : '确认完成' }}
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
import { connectWs, closeWs, onWsMessage, onWsConnected } from '@/utils/websocket'
import {
  getRepairListApi,
  createRepairApi,
  acceptRepairApi,
  completeRepairApi,
} from '@/api/repair'
import { getBaseEquipmentListApi } from '@/api/reference'
import { getRepairStatusLabel, getRepairStatusClass } from '@/types/repair'
import type { EqmRepairOrderVO } from '@/types/repair'
import type { BaseEquipmentVO } from '@/types/reference'

const userStore = useUserStore()

const loading = ref(false)
const loadError = ref('')
const submitting = ref(false)

const repairList = ref<EqmRepairOrderVO[]>([])
const equipmentRefs = ref<BaseEquipmentVO[]>([])

const activeRelation = ref('reported')

const relationFilters = computed(() => {
  const arr = [
    { label: '我的报修', value: 'reported' },
    { label: '我维修的', value: 'handled' },
  ]
  if (hasPermission('app:repair:view-pending')) {
    arr.splice(1, 0, { label: '待维修', value: 'pending' })
  }
  if (hasPermission('app:repair:view-all')) {
    arr.push({ label: '全部', value: 'all' })
  }
  return arr
})

watch(() => hasPermission('app:repair:view-all'), (canViewAll) => {
  if (!canViewAll && activeRelation.value === 'all') {
    activeRelation.value = 'reported'
  }
})
watch(() => hasPermission('app:repair:view-pending'), (canViewPending) => {
  if (!canViewPending && activeRelation.value === 'pending') {
    activeRelation.value = 'reported'
  }
})

const showCreateForm = ref(false)
const createForm = reactive({
  equipmentId: null as number | null,
  faultDesc: '',
})

const showCompleteForm = ref(false)
const completingItem = ref<EqmRepairOrderVO | null>(null)
const completeForm = reactive({ resultDesc: '' })

const canSubmitCreate = computed(() => !!createForm.equipmentId && createForm.faultDesc.trim() !== '')
const canSubmitComplete = computed(() => completeForm.resultDesc.trim() !== '')

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
  if (!hasPermission('app:repair:view')) {
    uni.showToast({ title: '暂无访问权限', icon: 'none' })
    uni.navigateBack()
    return
  }
  pageActive = true
  void fetchData()
  unsubscribeMessage = onWsMessage('REPAIR', refreshVisiblePage)
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
    const [repairs, equipment] = await Promise.all([
      getRepairListApi(buildListParams()),
      getBaseEquipmentListApi(),
    ])
    if (!pageActive || requestId !== fetchGeneration) return
    repairList.value = repairs
    equipmentRefs.value = equipment
  } catch (err) {
    if (!pageActive || requestId !== fetchGeneration) return
    console.error('[设备报修] 数据加载失败:', err)
    loadError.value = '数据加载失败，请检查网络后重试'
  } finally {
    if (pageActive && requestId === fetchGeneration) loading.value = false
  }
}

function getEquipmentName(equipmentId: number): string {
  const e = equipmentRefs.value.find((x) => x.equipmentId === equipmentId)
  return e ? e.equipmentName : `设备#${equipmentId}`
}

function formatTime(s: string | null): string {
  if (!s) return '—'
  return s.substring(0, 16).replace('T', ' ')
}

function openCreateForm(): void {
  createForm.equipmentId = null
  createForm.faultDesc = ''
  showCreateForm.value = true
}

function onEquipmentChange(e: { detail: { value: number } }): void {
  const opt = equipmentOptions.value[e.detail.value]
  createForm.equipmentId = opt?.value ?? null
}

async function submitCreate(): Promise<void> {
  if (!canSubmitCreate.value) {
    uni.showToast({ title: '请选择设备并填写故障描述', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await createRepairApi({
      equipmentId: createForm.equipmentId ?? undefined,
      faultDesc: createForm.faultDesc.trim(),
    })
    uni.showToast({ title: '报修成功', icon: 'success' })
    showCreateForm.value = false
    activeRelation.value = 'reported'
    await fetchData()
  } catch (err) {
    console.error('[设备报修] 提交报修失败:', err)
  } finally {
    submitting.value = false
  }
}

async function handleAccept(item: EqmRepairOrderVO): Promise<void> {
  uni.showModal({
    title: '接单维修',
    content: `确认接单维修单「#${item.repairId}」？`,
    success: async (res) => {
      if (!res.confirm) return
      submitting.value = true
      try {
        await acceptRepairApi(item.repairId)
        uni.showToast({ title: '接单成功', icon: 'success' })
        await fetchData()
      } catch (err) {
        console.error('[设备报修] 接单失败:', err)
      } finally {
        submitting.value = false
      }
    },
  })
}

function openCompleteForm(item: EqmRepairOrderVO): void {
  completingItem.value = item
  completeForm.resultDesc = ''
  showCompleteForm.value = true
}

async function submitComplete(): Promise<void> {
  if (!canSubmitComplete.value || !completingItem.value) {
    uni.showToast({ title: '请填写维修结果', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await completeRepairApi(completingItem.value.repairId, { resultDesc: completeForm.resultDesc.trim() })
    uni.showToast({ title: '维修完成', icon: 'success' })
    showCompleteForm.value = false
    await fetchData()
  } catch (err) {
    console.error('[设备报修] 完成维修失败:', err)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.repair-page { min-height: 100vh; padding: 0 24rpx 140rpx; background: var(--lux-porcelain-100); }
.filter-bar { padding: 20rpx 0 4rpx; }
.filter-scroll { white-space: nowrap; }
.filter-tag { display: inline-block; padding: 12rpx 30rpx; margin-right: 16rpx; border-radius: var(--lux-radius-xs); background: var(--lux-surface); color: var(--lux-text-faint); font-size: 25rpx; }
.filter-tag--active { background: var(--lux-ink-900); color: var(--lux-porcelain-50); }
.state-box { display: flex; flex-direction: column; align-items: center; padding: 140rpx 40rpx; }
.state-text { color: var(--lux-text-faint); font-size: 26rpx; }
.state-text--error { color: var(--lux-wine); }
.state-retry-btn { margin-top: 24rpx; padding: 14rpx 40rpx; border-radius: var(--lux-radius-xs); background: var(--lux-gold-50); }
.retry-text { color: var(--lux-gold-700); font-size: 25rpx; }
.empty-img { width: 120rpx; height: 120rpx; opacity: .8; }
.empty-title { margin-top: 20rpx; color: var(--lux-ink-900); font-size: 30rpx; font-weight: 600; }
.empty-desc { margin-top: 10rpx; color: var(--lux-text-faint); font-size: 24rpx; }
.repair-list { padding: 8rpx 0 20rpx; }
.repair-card { margin-top: 20rpx; padding: 26rpx 26rpx 10rpx; border: 1rpx solid var(--lux-line); border-radius: var(--lux-radius-md); background: var(--lux-surface); }
.card-header { display: flex; align-items: flex-start; justify-content: space-between; }
.header-left { display: flex; flex: 1; flex-direction: column; min-width: 0; }
.equip-name { color: var(--lux-ink-900); font-size: 29rpx; font-weight: 500; }
.repair-no { margin-top: 8rpx; color: var(--lux-text-faint); font-size: 22rpx; }
.status-tag { flex-shrink: 0; margin-left: 16rpx; padding: 6rpx 16rpx; border-radius: var(--lux-radius-xs); font-size: 22rpx; }
.status-tag.repair--pending { background: var(--lux-ember-50); color: var(--lux-ember-600); }
.status-tag.repair--processing { background: var(--lux-gold-50); color: var(--lux-gold-600); }
.status-tag.repair--done { background: var(--lux-jade-50); color: var(--lux-jade-600); }
.fault-box { margin-top: 18rpx; padding: 18rpx 20rpx; border-radius: var(--lux-radius-md); background: var(--lux-surface-soft); }
.fault-text { color: var(--lux-text-soft); font-size: 25rpx; line-height: 1.6; }
.card-body { margin-top: 14rpx; padding-top: 6rpx; border-top: 1rpx solid var(--lux-line-soft); }
.info-row { display: flex; justify-content: space-between; padding: 10rpx 0; }
.info-label { color: var(--lux-text-faint); font-size: 24rpx; }
.info-value { color: var(--lux-text-soft); font-size: 24rpx; max-width: 70%; text-align: right; }
.card-actions { display: flex; justify-content: flex-end; gap: 16rpx; padding: 16rpx 0 6rpx; border-top: 1rpx solid var(--lux-line-soft); }
.action-btn { flex: 1; max-width: 220rpx; margin: 0; padding: 0; height: 64rpx; line-height: 64rpx; border-radius: var(--lux-radius-sm); font-size: 26rpx; }
.action-btn::after { border: none; }
.action-btn--accept { background: var(--lux-gold-gradient); color: #17130B; }
.action-btn--complete { background: var(--lux-jade-50); color: var(--lux-jade-600); }
.bottom-bar { position: fixed; left: 0; right: 0; bottom: 0; z-index: 20; padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom)); background: var(--lux-surface); box-shadow: var(--lux-shadow-bar); }
.create-btn { height: 88rpx; line-height: 88rpx; border-radius: var(--lux-radius-sm); background: var(--lux-gold-gradient); color: #17130B; font-size: 30rpx; font-weight: 600; }
.create-btn::after { border: none; }
.bottom-placeholder { height: 20rpx; }
.modal-mask { position: fixed; inset: 0; z-index: 100; display: flex; align-items: flex-end; background: rgba(15, 23, 42, .45); }
.modal-panel { width: 100%; padding: 30rpx 30rpx calc(30rpx + env(safe-area-inset-bottom)); border-radius: var(--lux-radius-md) var(--lux-radius-md) 0 0; background: var(--lux-surface); }
.modal-header { display: flex; align-items: center; justify-content: space-between; padding-bottom: 20rpx; }
.modal-title { color: var(--lux-ink-900); font-size: 32rpx; font-weight: 600; }
.modal-close { color: var(--lux-text-faint); font-size: 34rpx; padding: 0 10rpx; }
.modal-body { max-height: 62vh; overflow-y: auto; }
.form-item { margin-top: 24rpx; }
.form-label { display: block; color: var(--lux-text-soft); font-size: 26rpx; margin-bottom: 12rpx; }
.form-required { color: var(--lux-wine); }
.form-picker { width: 100%; }
.form-picker-value { padding: 20rpx 24rpx; border: 1rpx solid var(--lux-line); border-radius: var(--lux-radius-sm); background: var(--lux-surface-soft); color: var(--lux-text-soft); font-size: 26rpx; }
.form-picker-value--empty { color: var(--lux-text-faint); }
.form-textarea { width: 100%; box-sizing: border-box; min-height: 160rpx; padding: 20rpx 24rpx; border: 1rpx solid var(--lux-line); border-radius: var(--lux-radius-sm); background: var(--lux-surface-soft); font-size: 26rpx; }
.modal-footer { display: flex; gap: 20rpx; margin-top: 36rpx; }
.modal-btn { flex: 1; height: 84rpx; line-height: 84rpx; border-radius: var(--lux-radius-sm); font-size: 28rpx; margin: 0; }
.modal-btn::after { border: none; }
.modal-btn--cancel { background: var(--lux-porcelain-200); color: var(--lux-text-faint); }
.modal-btn--confirm { background: var(--lux-gold-gradient); color: #17130B; }
.modal-btn--confirm[disabled] { background: var(--lux-porcelain-200); color: #17130B; }
</style>
