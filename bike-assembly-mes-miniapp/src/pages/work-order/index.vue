<template>
  <view class="wo-page">
    <!-- 搜索栏 -->
    <view class="search-bar">
      <view class="search-input-wrap">
        <image class="search-icon" src="/static/icons/search.svg" mode="aspectFit" />
        <input
          class="search-input"
          v-model="searchText"
          placeholder="搜索工单编号或产品名称"
          placeholder-style="color: #a7b1c2"
          confirm-type="search"
          @confirm="onSearch"
        />
        <text v-if="searchText" class="search-clear" @click="searchText = ''">✕</text>
      </view>
    </view>

    <!-- 数据范围筛选（仅全部数据权限可见） -->
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

    <!-- 状态筛选 -->
    <view class="filter-bar">
      <scroll-view scroll-x class="filter-scroll">
        <view
          v-for="f in statusFilters"
          :key="f.value"
          class="filter-tag"
          :class="{ 'filter-tag--active': activeFilter === f.value }"
          @click="onFilterChange(f.value)"
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
    <view v-else-if="filteredList.length === 0" class="state-box">
      <image class="empty-img" src="/static/icons/empty-order.svg" mode="aspectFit" />
      <text class="empty-title">暂无工单数据</text>
      <text class="empty-desc">当前没有符合条件的工单</text>
    </view>

    <!-- 工单列表 -->
    <view v-else class="wo-list">
      <view
        v-for="order in filteredList"
        :key="order.workOrderId"
        class="wo-card"
        @click="goToDetail(order.workOrderId)"
      >
        <view class="wo-card-header">
          <text class="wo-no">{{ order.workOrderNo }}</text>
          <text class="status-tag" :class="'status-tag--' + getWorkOrderStatusClass(order.status)">
            {{ getWorkOrderStatusLabel(order.status) }}
          </text>
        </view>
        <view class="wo-card-body">
          <view class="wo-info-row">
            <text class="wo-info-label">产品</text>
            <text class="wo-info-value wo-info-value--ellipsis">{{ getItemName(order.itemId) }}</text>
          </view>
          <view class="wo-info-row">
            <text class="wo-info-label">计划数量</text>
            <text class="wo-info-value">{{ order.planQty }} {{ order.unit }}</text>
          </view>
          <view class="wo-info-row">
            <text class="wo-info-label">计划时间</text>
            <text class="wo-info-value">
              {{ formatDateRange(order.planStartTime, order.planEndTime) }}
            </text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'
import { hasPermission, hasAllDataScope } from '@/utils/permission'
import { getWorkOrderListApi } from '@/api/workOrder'
import { getBaseItemListApi } from '@/api/reference'
import { getWorkOrderStatusLabel, getWorkOrderStatusClass } from '@/types/workOrder'
import type { WorkOrderVO } from '@/types/workOrder'
import type { BaseItemVO } from '@/types/reference'

const userStore = useUserStore()

const loading = ref(false)
const loadError = ref('')
const searchText = ref('')
const activeFilter = ref('')
const activeScope = ref('mine')

// 只有拥有 view-all 权限时才显示"全部工单"选项
const scopeFilters = computed(() => {
  const filters = [{ label: '我的工单', value: 'mine' }]
  if (hasPermission('app:work-order:view-all')) {
    filters.push({ label: '全部工单', value: 'all' })
  }
  return filters
})

// 权限变化后如果当前为 all 但失去权限，自动切回 mine
watch(() => hasPermission('app:work-order:view-all'), (canViewAll) => {
  if (!canViewAll && activeScope.value === 'all') {
    activeScope.value = 'mine'
  }
})

const orderList = ref<WorkOrderVO[]>([])
const itemMap = ref<Record<number, string>>({})

const statusFilters = [
  { label: '全部', value: '' },
  { label: '已创建', value: '0' },
  { label: '已下达', value: '4' },
  { label: '生产中', value: '1' },
  { label: '已暂停', value: '5' },
  { label: '已完工', value: '2' },
  { label: '已关闭', value: '3' },
  { label: '已取消', value: '6' },
]

//对原始工单列表做筛选过滤
const filteredList = computed(() => {
  let list = orderList.value
  if (activeFilter.value !== '') {
    list = list.filter((o) => o.status === activeFilter.value)
  }
  const keyword = searchText.value.trim().toLowerCase()
  if (keyword) {
    list = list.filter((o) => {
      const itemName = getItemName(o.itemId).toLowerCase()
      return o.workOrderNo.toLowerCase().includes(keyword) || itemName.includes(keyword)
    })
  }
  return list
})

// [前端调用] 页面每次显示时：检查登录状态 → 检查权限 → 拉取工单数据
onShow(() => {
  if (!userStore.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  // [权限控制] 前端权限检查（仅隐藏 UI，后端 @RequirePermission 做真正鉴权）
  if (!hasPermission('app:work-order:view')) {
    uni.showToast({ title: '暂无访问权限', icon: 'none' })
    uni.navigateBack()
    return
  }
  fetchData()
})

onPullDownRefresh(() => {
  fetchData().finally(() => {
    uni.stopPullDownRefresh()
  })
})

// [前端调用] scope 切换时立即重新加载数据（mine ↔ all）
watch(activeScope, () => {
  fetchData()
})

/**
 * [前端调用] 拉取工单列表和物料参考数据。
 * 调用链：fetchData → getWorkOrderListApi() → GET /app/pro-work-order/list → AppProWorkOrderController.list
 *   同时通过 getBaseItemListApi() 获取物料名称映射表（itemId → itemName），用于列表展示产品名称
 * [数据转换] 返回的工单按 createTime 降序排列，物料名称存入 itemMap 供 getItemName() 查询
 */
async function fetchData(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    // [前端调用] 并发请求工单列表和物料列表
    const [orders, items] = await Promise.all([
      getWorkOrderListApi(activeScope.value),
      getBaseItemListApi()
    ])
    orders.sort((a, b) => {
      if (a.createTime && b.createTime) {
        return b.createTime.localeCompare(a.createTime)
      }
      return 0
    })
    orderList.value = orders
    const map: Record<number, string> = {}
    items.forEach((item: BaseItemVO) => {
      map[item.itemId] = item.itemName
    })
    itemMap.value = map
  } catch {
    loadError.value = '数据加载失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

function getItemName(itemId: number): string {
  return itemMap.value[itemId] || `物料#${itemId}`
}

function formatDateRange(start: string | null, end: string | null): string {
  const fmt = (s: string | null): string => {
    if (!s) return '—'
    return s.substring(0, 10)
  }
  return `${fmt(start)} ~ ${fmt(end)}`
}

function onFilterChange(value: string): void {
  activeFilter.value = value
}

function onSearch(): void {
  // computed 自动响应
}

function goToDetail(workOrderId: number): void {
  uni.navigateTo({ url: `/pages/work-order/detail?id=${workOrderId}` })
}
</script>

<style scoped>
.wo-page {
  min-height: 100vh;
  background: var(--lux-porcelain-100);
  padding-bottom: env(safe-area-inset-bottom);
}

/* ========== 搜索栏 ========== */
.search-bar {
  padding: 20rpx 28rpx;
  background: var(--lux-surface);
}

.search-input-wrap {
  display: flex;
  align-items: center;
  background: var(--lux-surface-soft);
  border-radius: var(--lux-radius-sm);
  padding: 0 24rpx;
  height: 72rpx;
}

.search-icon {
  width: 32rpx;
  height: 32rpx;
  margin-right: 12rpx;
  opacity: 0.4;
}

.search-input {
  flex: 1;
  font-size: 28rpx;
  color: var(--lux-ink-900);
}

.search-clear {
  font-size: 28rpx;
  color: var(--lux-text-faint);
  padding: 8rpx;
}

/* ========== 筛选栏 ========== */
.filter-bar {
  background: var(--lux-surface);
  padding: 0 28rpx 20rpx;
}

.scope-bar {
  padding-top: 4rpx;
}

.filter-scroll {
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

/* ========== 状态区域 ========== */
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
  transition: background 0.15s ease;
}

.state-retry-btn--hover {
  background: var(--lux-gold-50);
}

.retry-text {
  font-size: 26rpx;
  color: var(--lux-gold-700);
  font-weight: 500;
}

/* ========== 工单列表 ========== */
.wo-list {
  padding: 16rpx 28rpx;
}

.wo-card {
  background: var(--lux-surface);
  border-radius: var(--lux-radius-md);
  padding: 28rpx;
  margin-bottom: 16rpx;
  border: 1rpx solid var(--lux-line);
}

.wo-card:active {
  background: var(--lux-porcelain-200);
}

.wo-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.wo-no {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--lux-ink-900);
}

/* 状态标签 */
.status-tag {
  font-size: 22rpx;
  padding: 6rpx 14rpx;
  border-radius: 8rpx;
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

.wo-card-body {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.wo-info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.wo-info-label {
  font-size: 26rpx;
  color: var(--lux-text-faint);
  flex-shrink: 0;
}

.wo-info-value {
  font-size: 26rpx;
  color: var(--lux-text);
  text-align: right;
  max-width: 400rpx;
}

.wo-info-value--ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
