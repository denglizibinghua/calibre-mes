<template>
  <view class="home-page">
    <view class="hero-area" :style="{ paddingTop: contentTop + 'px' }">
      <view class="hero-orbit hero-orbit--one" />
      <view class="hero-orbit hero-orbit--two" />
      <view class="top-brand-row">
        <view class="brand-mark">
          <image class="brand-logo" src="/static/logo.png" mode="aspectFit" />
        </view>
        <view class="brand-copy">
          <text class="brand-title">自行车装配 MES</text>
          <text class="brand-subtitle">移动生产作业台</text>
        </view>
        <view class="online-pill">
          <view class="online-dot" />
          <text>在线</text>
        </view>
      </view>

      <view class="operator-card">
        <view class="operator-main">
          <text class="greeting-text">{{ greetingText }}</text>
          <text class="user-name">{{ userStore.nickname || userStore.username || '用户' }}</text>
          <text class="welcome-subtitle">规范作业，及时报工，保障装配质量</text>
        </view>
        <view class="scope-panel" :class="hasAllDataScope() ? 'scope-panel--all' : 'scope-panel--self'">
          <text class="scope-caption">数据范围</text>
          <view class="scope-value-row">
            <view class="scope-dot" />
            <text class="scope-value">{{ hasAllDataScope() ? '全部数据' : '我的数据' }}</text>
          </view>
        </view>
      </view>
    </view>

    <view class="workspace">
      <view class="section-heading">
        <view>
          <text class="section-title">生产作业</text>
          <text class="section-desc">选择功能进入对应业务流程</text>
        </view>
        <view class="section-badge">{{ menuItems.length }} 项可用</view>
      </view>

      <view class="func-grid">
        <view
          v-for="item in menuItems"
          :key="item.key"
          class="func-card"
          hover-class="func-card--hover"
          :hover-stay-time="70"
          @click="onMenuClick(item)"
        >
          <view class="func-card-top">
            <view class="func-icon-wrap" :style="{ backgroundColor: item.iconBg }">
              <image class="func-icon" :src="item.iconSrc" mode="aspectFit" />
            </view>
            <view class="func-arrow-wrap">
              <image class="func-arrow" src="/static/icons/arrow-right.svg" mode="aspectFit" />
            </view>
          </view>
          <text class="func-title">{{ item.title }}</text>
          <text class="func-desc">{{ item.desc }}</text>
          <view class="func-line" />
        </view>

        <view v-if="menuItems.length === 0 && allMenuItems.length > 0" class="empty-state">
          <view class="empty-icon-wrap">
            <image class="empty-state-icon" src="/static/icons/lock.svg" mode="aspectFit" />
          </view>
          <text class="empty-state-title">暂无可用功能</text>
          <text class="empty-state-desc">当前账号尚未分配移动端操作权限，请联系系统管理员</text>
        </view>
      </view>

      <view class="notice-card">
        <view class="notice-icon"><text>i</text></view>
        <view class="notice-copy">
          <text class="notice-title">作业提示</text>
          <text class="notice-desc">请按工单流程完成报工、质检和异常上报，确保生产记录及时准确。</text>
        </view>
      </view>
    </view>

    <view class="bottom-placeholder" />
    <TabBar />
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'
import { updateActiveTab } from '@/stores/tabBar'
import { hasPermission, hasAllDataScope } from '@/utils/permission'
import TabBar from '@/components/tab-bar/index.vue'

import { getCustomContentTop } from '@/utils/customNavigation'

const contentTop = ref(getCustomContentTop())
const userStore = useUserStore()

let lastRefreshTime = 0
const REFRESH_INTERVAL = 60_000

/** 问候语 */
const greetingText = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了，注意休息'
  if (hour < 9) return '早上好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

/** 功能入口数据 */
interface MenuItem {
  key: string
  iconSrc: string
  iconBg: string
  title: string
  desc: string
  permission: string
}

const iconBasePath = '/static/icons/'

const allMenuItems: MenuItem[] = [
  {
    key: 'workOrder', iconSrc: iconBasePath + 'work-order.svg', iconBg: '#eef2ff',
    title: '我的工单', desc: '查看与处理生产工单', permission: 'app:work-order:view',
  },
  {
    key: 'workProcess', iconSrc: iconBasePath + 'work-process.svg', iconBg: '#eff6ff',
    title: '工序报工', desc: '生产工序进度上报', permission: 'app:work-process:view',
  },
  {
    key: 'inspection', iconSrc: iconBasePath + 'inspection.svg', iconBg: '#f0faf4',
    title: '质量检验', desc: '质检结果录入与查询', permission: 'app:inspection:view',
  },
  {
    key: 'andon', iconSrc: iconBasePath + 'andon.svg', iconBg: '#fff7ed',
    title: '安灯告警', desc: '异常事件快速上报', permission: 'app:andon:view',
  },
  {
    key: 'repair', iconSrc: iconBasePath + 'repair.svg', iconBg: '#f3f0ff',
    title: '设备报修', desc: '设备故障报修处理', permission: 'app:repair:view',
  },
  {
    key: 'inventory', iconSrc: iconBasePath + 'inventory.svg', iconBg: '#fef3f2',
    title: '库存查询', desc: '物料库存实时查询', permission: 'app:inventory:view',
  },
]

const menuItems = computed<MenuItem[]>(() =>
  allMenuItems.filter((item) => hasPermission(item.permission)),
)

onShow(async () => {
  contentTop.value = getCustomContentTop()
  updateActiveTab()
  if (!userStore.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  const now = Date.now()
  if (now - lastRefreshTime > REFRESH_INTERVAL) {
    lastRefreshTime = now
    try {
      await userStore.refreshProfile()
    } catch {
      uni.showToast({ title: '权限信息加载失败，部分功能可能不可用', icon: 'none', duration: 2000 })
    }
  }
})

onHide(() => {
  lastRefreshTime = 0
})

function onMenuClick(item: MenuItem): void {
  const routeMap: Record<string, string> = {
    workOrder: '/pages/work-order/index',
    workProcess: '/pages/work-process/index',
    inspection: '/pages/inspection/index',
    andon: '/pages/andon/index',
    repair: '/pages/repair/index',
    inventory: '/pages/inventory/index',
  }

  const url = routeMap[item.key]
  if (url) {
    uni.navigateTo({ url })
  } else {
    uni.showToast({ title: `${item.title} — 功能开发中`, icon: 'none' })
  }
}
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  background: var(--lux-porcelain-100);
  padding-bottom: env(safe-area-inset-bottom);
}

.hero-area {
  position: relative;
  padding: calc(env(safe-area-inset-top, 0rpx) + 24rpx) 28rpx 32rpx;
  overflow: hidden;
  background: var(--lux-hero-gradient);
  border-bottom: 1rpx solid var(--lux-shell-line-strong);
}

.hero-orbit {
  position: absolute;
  border: 1rpx solid var(--lux-shell-line);
  border-radius: var(--lux-radius-round);
  pointer-events: none;
}

.hero-orbit--one { width: 360rpx; height: 360rpx; top: -190rpx; right: -100rpx; }
.hero-orbit--two { width: 210rpx; height: 210rpx; right: 38rpx; bottom: -130rpx; }

.top-brand-row { position: relative; z-index: 2; display: flex; align-items: center; }
.brand-mark {
  display: flex; align-items: center; justify-content: center;
  width: 70rpx; height: 70rpx; flex-shrink: 0; border-radius: var(--lux-radius-sm);
  background: rgba(255, 255, 255, 0.04); border: 1rpx solid var(--lux-shell-line-strong);
}
.brand-logo { width: 44rpx; height: 44rpx; }
.brand-copy { display: flex; min-width: 0; flex: 1; flex-direction: column; margin-left: 16rpx; }
.brand-title { color: var(--lux-shell-text); font-size: 28rpx; font-weight: 600; letter-spacing: 1rpx; }
.brand-subtitle { margin-top: 2rpx; color: var(--lux-shell-muted); font-size: 21rpx; letter-spacing: 1rpx; }
.online-pill {
  display: flex; align-items: center; padding: 8rpx 16rpx; border: 1rpx solid var(--lux-shell-line); border-radius: var(--lux-radius-xs);
  background: rgba(198, 169, 110, .08); color: var(--lux-shell-text); font-size: 21rpx; letter-spacing: 1rpx;
}
.online-dot { width: 10rpx; height: 10rpx; margin-right: 8rpx; border-radius: var(--lux-radius-round); background: var(--lux-jade-400); box-shadow: 0 0 0 6rpx rgba(63, 143, 121, .14); }

.operator-card {
  position: relative; z-index: 2; display: flex; align-items: stretch; justify-content: space-between;
  margin-top: 36rpx; padding: 30rpx; border: 1rpx solid var(--lux-shell-line-strong); border-radius: var(--lux-radius-sm);
  background: var(--lux-ink-800);
}
.operator-main { display: flex; min-width: 0; flex: 1; flex-direction: column; padding-right: 18rpx; }
.greeting-text { color: var(--lux-shell-muted); font-size: 23rpx; }
.user-name { margin-top: 8rpx; color: var(--lux-shell-text); font-size: 40rpx; font-weight: 600; letter-spacing: 1rpx; }
.welcome-subtitle { margin-top: 12rpx; color: var(--lux-shell-muted); font-size: 22rpx; line-height: 1.55; }
.scope-panel {
  display: flex; width: 174rpx; flex-shrink: 0; flex-direction: column; justify-content: center;
  padding: 18rpx; border: 1rpx solid var(--lux-shell-line); border-radius: var(--lux-radius-xs);
  background: rgba(198, 169, 110, .06);
}
.scope-caption { color: var(--lux-shell-muted); font-size: 20rpx; letter-spacing: 1rpx; }
.scope-value-row { display: flex; align-items: center; margin-top: 10rpx; }
.scope-dot { width: 10rpx; height: 10rpx; margin-right: 9rpx; border-radius: var(--lux-radius-round); background: var(--lux-gold-400); }
.scope-panel--all .scope-dot { background: var(--lux-jade-400); }
.scope-value { color: var(--lux-shell-text); font-size: 23rpx; font-weight: 500; }

.workspace { position: relative; z-index: 3; margin-top: 0; padding: 0 28rpx; }
.section-heading {
  display: flex; align-items: flex-end; justify-content: space-between; padding: 30rpx 6rpx 22rpx;
}
.section-heading > view:first-child { display: flex; flex-direction: column; }
.section-title { color: var(--lux-ink-900); font-size: 34rpx; font-weight: 600; letter-spacing: 1rpx; }
.section-desc { margin-top: 6rpx; color: var(--lux-text-muted); font-size: 23rpx; }
.section-badge { padding: 7rpx 14rpx; border: 1rpx solid var(--lux-gold-600); border-radius: var(--lux-radius-xs); background: var(--lux-gold-50); color: var(--lux-gold-700); font-size: 21rpx; font-weight: 500; letter-spacing: 1rpx; }

.func-grid { display: flex; flex-wrap: wrap; gap: 18rpx; }
.func-card {
  position: relative; width: calc((100% - 18rpx) / 2); min-height: 236rpx; padding: 24rpx;
  overflow: hidden; border: 1rpx solid var(--lux-line); border-radius: var(--lux-radius-md); background: var(--lux-surface);
  box-shadow: var(--lux-shadow-card); transition: all .18s ease;
}
.func-card--hover { transform: translateY(-2rpx); background: var(--lux-surface-soft); box-shadow: var(--lux-shadow-pop); }
.func-card-top { display: flex; align-items: flex-start; justify-content: space-between; }
.func-icon-wrap { display: flex; align-items: center; justify-content: center; width: 76rpx; height: 76rpx; border: 1rpx solid var(--lux-line); border-radius: var(--lux-radius-xs); background: var(--lux-surface-soft) !important; }
.func-icon { width: 40rpx; height: 40rpx; }
.func-arrow-wrap { display: flex; align-items: center; justify-content: center; width: 42rpx; height: 42rpx; border-radius: var(--lux-radius-xs); background: var(--lux-porcelain-200); }
.func-arrow { width: 24rpx; height: 24rpx; opacity: .55; }
.func-title { display: block; margin-top: 23rpx; color: var(--lux-ink-900); font-size: 29rpx; font-weight: 500; letter-spacing: 1rpx; }
.func-desc { display: block; margin-top: 8rpx; color: var(--lux-text-muted); font-size: 21rpx; line-height: 1.45; }
.func-line { position: absolute; right: 24rpx; bottom: 0; left: 24rpx; height: 3rpx; border-radius: var(--lux-radius-xs) var(--lux-radius-xs) 0 0; background: var(--lux-gold-500); opacity: .9; }

.empty-state {
  display: flex; width: 100%; flex-direction: column; align-items: center; padding: 86rpx 32rpx;
  border: 1rpx dashed var(--lux-line-strong); border-radius: var(--lux-radius-md); background: var(--lux-surface);
}
.empty-icon-wrap { display: flex; align-items: center; justify-content: center; width: 96rpx; height: 96rpx; border-radius: var(--lux-radius-sm); background: var(--lux-porcelain-200); }
.empty-state-icon { width: 46rpx; height: 46rpx; opacity: .55; }
.empty-state-title { margin-top: 22rpx; color: var(--lux-text); font-size: 29rpx; font-weight: 500; letter-spacing: 1rpx; }
.empty-state-desc { margin-top: 10rpx; padding: 0 30rpx; color: var(--lux-text-muted); font-size: 23rpx; line-height: 1.6; text-align: center; }

.notice-card {
  display: flex; align-items: flex-start; margin-top: 22rpx; padding: 24rpx;
  border: 1rpx solid var(--lux-line); border-left: 2rpx solid var(--lux-gold-500);
  border-radius: var(--lux-radius-sm); background: var(--lux-surface-soft);
}
.notice-icon { display: flex; align-items: center; justify-content: center; width: 42rpx; height: 42rpx; flex-shrink: 0; border-radius: var(--lux-radius-xs); background: var(--lux-gold-600); color: #17130B; font-size: 24rpx; font-weight: 600; }
.notice-copy { display: flex; flex-direction: column; margin-left: 16rpx; }
.notice-title { color: var(--lux-ink-900); font-size: 25rpx; font-weight: 500; letter-spacing: 1rpx; }
.notice-desc { margin-top: 5rpx; color: var(--lux-text-muted); font-size: 22rpx; line-height: 1.6; }
.bottom-placeholder { height: calc(154rpx + env(safe-area-inset-bottom, 0rpx)); }
</style>
