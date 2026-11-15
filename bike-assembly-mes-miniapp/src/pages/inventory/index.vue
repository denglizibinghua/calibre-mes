<template>
  <view class="inv-page">
    <!-- 搜索栏 -->
    <view class="search-bar">
      <view class="search-input-wrap">
        <image class="search-icon" src="/static/icons/search.svg" mode="aspectFit" />
        <input
          class="search-input"
          v-model="searchKeyword"
          placeholder="输入物料名称或编码搜索"
          placeholder-style="color: #a7b1c2"
          @confirm="onSearch"
        />
        <text v-if="searchKeyword" class="search-clear" @click="clearSearch">✕</text>
      </view>
    </view>

    <!-- 摘要 -->
    <view class="summary-bar">
      <view class="summary-item">
        <text class="summary-num">{{ summary.total }}</text>
        <text class="summary-label">库存条目</text>
      </view>
      <view class="summary-item">
        <text class="summary-num summary-num--ok">{{ summary.ok }}</text>
        <text class="summary-label">充足</text>
      </view>
      <view class="summary-item">
        <text class="summary-num summary-num--low">{{ summary.low }}</text>
        <text class="summary-label">低库存</text>
      </view>
      <view class="summary-item">
        <text class="summary-num summary-num--out">{{ summary.out }}</text>
        <text class="summary-label">缺货</text>
      </view>
    </view>

    <!-- 加载中 -->
    <view v-if="loading" class="state-box"><text class="state-text">加载中...</text></view>

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
      <text class="empty-title">{{ searchKeyword ? '未找到匹配物料' : '暂无库存数据' }}</text>
    </view>

    <!-- 库存列表 -->
    <view v-else class="inventory-list">
      <view
        v-for="inv in filteredList"
        :key="inv.inventoryId"
        class="inventory-card"
      >
        <view class="card-header">
          <view class="header-left">
            <text class="item-name">{{ inv.itemName }}</text>
            <text class="item-code">{{ inv.itemCode }}</text>
          </view>
          <view class="header-right">
            <text class="stock-badge" :class="getStockStatus(inv.qty).class">
              {{ getStockStatus(inv.qty).label }}
            </text>
          </view>
        </view>

        <view class="card-body">
          <view class="qty-row">
            <view class="qty-item">
              <text class="qty-label">库存数量</text>
              <text class="qty-value">{{ formatQty(inv.qty) }} {{ inv.unit }}</text>
            </view>
            <view class="qty-item">
              <text class="qty-label">锁定数量</text>
              <text class="qty-value qty-value--locked">{{ formatQty(inv.lockedQty) }} {{ inv.unit }}</text>
            </view>
            <view class="qty-item">
              <text class="qty-label">可用数量</text>
              <text
                class="qty-value qty-value--available"
                :class="inv.qty - inv.lockedQty <= 0 ? 'text--danger' : 'text--success'"
              >
                {{ formatQty(inv.qty - inv.lockedQty) }} {{ inv.unit }}
              </text>
            </view>
          </view>

          <view class="info-row">
            <view class="info-item">
              <text class="info-label">库位</text>
              <text class="info-value">{{ inv.locationName || inv.locationCode || '—' }}</text>
            </view>
            <view class="info-item">
              <text class="info-label">批次号</text>
              <text class="info-value">{{ inv.batchNo || '—' }}</text>
            </view>
            <view class="info-item">
              <text class="info-label">更新时间</text>
              <text class="info-value">{{ formatTime(inv.updateTime) }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'
import { hasPermission } from '@/utils/permission'
import { getInventoryListApi } from '@/api/inventory'
import { getStockStatus } from '@/types/inventory'
import type { WmsInventoryVO } from '@/types/inventory'

const userStore = useUserStore()

const loading = ref(false)
const loadError = ref('')
const inventoryList = ref<WmsInventoryVO[]>([])
const searchKeyword = ref('')

const summary = computed(() => {
  const total = inventoryList.value.length
  const ok = inventoryList.value.filter((i) => i.qty >= 10).length
  const low = inventoryList.value.filter((i) => i.qty > 0 && i.qty < 10).length
  const out = inventoryList.value.filter((i) => i.qty <= 0).length
  return { total, ok, low, out }
})

const filteredList = computed(() => {
  if (!searchKeyword.value.trim()) return inventoryList.value
  const kw = searchKeyword.value.trim().toLowerCase()
  return inventoryList.value.filter(
    (i) =>
      i.itemName?.toLowerCase().includes(kw) ||
      i.itemCode?.toLowerCase().includes(kw),
  )
})

onShow(() => {
  if (!userStore.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  if (!hasPermission('app:inventory:view')) {
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
  loading.value = true
  loadError.value = ''
  try {
    const list = await getInventoryListApi()
    inventoryList.value = list
  } catch {
    loadError.value = '数据加载失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  // frontend local filter
}

function clearSearch(): void {
  searchKeyword.value = ''
}

function formatQty(qty: number): string {
  return Number(qty.toFixed(2)).toString()
}

function formatTime(s: string | null): string {
  if (!s) return '—'
  return s.substring(0, 16).replace('T', ' ')
}
</script>

<style scoped>
.inv-page {
  min-height: 100vh;
  background: var(--lux-porcelain-100);
  padding-bottom: env(safe-area-inset-bottom);
}

/* ========== 搜索栏 ========== */
.search-bar {
  background: var(--lux-surface);
  padding: 20rpx 28rpx;
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

/* ========== 摘要栏 ========== */
.summary-bar {
  display: flex;
  background: var(--lux-surface);
  padding: 24rpx 20rpx;
  justify-content: space-around;
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

.summary-num--ok { color: var(--lux-jade); }
.summary-num--low { color: var(--lux-ember); }
.summary-num--out { color: var(--lux-wine); }

.summary-label {
  font-size: 24rpx;
  color: var(--lux-text-faint);
  margin-top: 4rpx;
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
}

.state-text { font-size: 28rpx; color: var(--lux-text-faint); }

.state-text--error { color: var(--lux-wine); margin-bottom: 24rpx; }

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

.state-retry-btn--hover { background: var(--lux-gold-50); }

.retry-text {
  font-size: 26rpx;
  color: var(--lux-gold-700);
  font-weight: 500;
}

/* ========== 库存列表 ========== */
.inventory-list {
  padding: 16rpx 28rpx 40rpx;
}

.inventory-card {
  background: var(--lux-surface);
  border-radius: var(--lux-radius-md);
  padding: 28rpx;
  margin-bottom: 16rpx;
  border: 1rpx solid var(--lux-line);
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
  flex: 1;
}

.item-name {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--lux-ink-900);
}

.item-code {
  font-size: 24rpx;
  color: var(--lux-text-faint);
  margin-top: 6rpx;
}

.stock-badge {
  font-size: 22rpx;
  padding: 6rpx 14rpx;
  border-radius: 8rpx;
  font-weight: 500;
  flex-shrink: 0;
}

.stock--ok { color: var(--lux-jade); background: var(--lux-jade-50); }
.stock--low { color: var(--lux-ember); background: var(--lux-ember-50); }
.stock--out { color: var(--lux-wine); background: var(--lux-wine-50); }

/* ========== 数量行 ========== */
.qty-row {
  display: flex;
  background: var(--lux-surface-soft);
  border-radius: 12rpx;
  padding: 20rpx;
  margin-bottom: 16rpx;
}

.qty-item {
  flex: 1;
  text-align: center;
}

.qty-label {
  display: block;
  font-size: 22rpx;
  color: var(--lux-text-faint);
  margin-bottom: 8rpx;
}

.qty-value {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--lux-ink-900);
}

.qty-value--locked { color: var(--lux-ember); }
.qty-value--available { font-size: 30rpx; }

.text--success { color: var(--lux-jade); }
.text--danger { color: var(--lux-wine); }

/* ========== 信息行 ========== */
.info-row {
  display: flex;
  flex-wrap: wrap;
}

.info-item {
  flex: 1 1 50%;
  margin-bottom: 10rpx;
}

.info-label {
  display: block;
  font-size: 22rpx;
  color: var(--lux-text-faint);
  margin-bottom: 4rpx;
}

.info-value {
  font-size: 24rpx;
  color: var(--lux-text);
}
</style>
