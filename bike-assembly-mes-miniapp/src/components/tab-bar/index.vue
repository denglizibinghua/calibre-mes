<template>
  <view class="tab-bar">
    <view class="tab-bar-bg" />

    <!-- 首页 -->
    <view
      class="tab-item"
      hover-class="tab-item--hover"
      @click="switchTo(0)"
    >
      <image
        class="tab-icon"
        :src="activeTabIndex === 0 ? '/static/icons/home-active.svg' : '/static/icons/home.svg'"
        mode="aspectFit"
      />
      <text class="tab-text" :class="{ 'tab-text--active': activeTabIndex === 0 }">首页</text>
    </view>

    <!-- 中间扫码按钮 -->
    <view class="tab-item tab-item--center">
      <view class="scan-btn" hover-class="scan-btn--hover" @click="handleScan">
        <image class="scan-icon" src="/static/icons/scan.svg" mode="aspectFit" />
      </view>
      <text class="tab-text tab-text--scan">扫码</text>
    </view>

    <!-- 我的 -->
    <view
      class="tab-item"
      hover-class="tab-item--hover"
      @click="switchTo(1)"
    >
      <image
        class="tab-icon"
        :src="activeTabIndex === 1 ? '/static/icons/profile-active.svg' : '/static/icons/profile.svg'"
        mode="aspectFit"
      />
      <text class="tab-text" :class="{ 'tab-text--active': activeTabIndex === 1 }">我的</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { activeTabIndex, switchToTab } from '@/stores/tabBar'

import { createWorkOrderScanner } from '@/utils/workOrderScan'

const handleScan = createWorkOrderScanner()

function switchTo(index: number): void {
  switchToTab(index)
}
</script>

<style scoped>
.tab-bar {
  position: fixed;
  z-index: 999;
  right: 24rpx;
  bottom: calc(16rpx + env(safe-area-inset-bottom, 0rpx));
  left: 24rpx;
  display: flex;
  align-items: center;
  justify-content: space-around;
  height: 104rpx;
  border: 1rpx solid var(--lux-shell-line);
  border-top: 1rpx solid var(--lux-gold-600);
  border-radius: var(--lux-radius-sm);
  background: var(--lux-ink-900);
  box-shadow: inset 0 1rpx 0 var(--lux-shell-line-strong), 0 16rpx 36rpx rgba(7, 10, 15, .28);
}
.tab-bar-bg { display: none; }
.tab-item {
  position: relative; z-index: 2; display: flex; flex: 1; height: 100%;
  flex-direction: column; align-items: center; justify-content: center;
}
.tab-item--hover { opacity: .72; }
.tab-icon { width: 42rpx; height: 42rpx; }
.tab-text { margin-top: 4rpx; color: var(--lux-shell-muted); font-size: 20rpx; letter-spacing: 1rpx; }
.tab-text--active { color: var(--lux-gold-500); font-weight: 500; }
.tab-item--center { position: relative; justify-content: flex-end; padding-bottom: 12rpx; }
.scan-btn {
  position: absolute; top: -34rpx; display: flex; align-items: center; justify-content: center;
  width: 104rpx; height: 104rpx;
  border: 6rpx solid var(--lux-ink-950);
  border-radius: var(--lux-radius-md);
  background: var(--lux-gold-gradient);
  box-shadow: 0 12rpx 24rpx rgba(120, 94, 44, .28);
  transition: transform .15s ease;
}
.scan-btn::after { content: ''; position: absolute; inset: 12rpx; border: 1rpx solid rgba(23, 19, 11, .18); border-radius: var(--lux-radius-sm); }
.scan-btn--hover { transform: scale(.93); }
.scan-icon { position: relative; z-index: 2; width: 46rpx; height: 46rpx; }
.tab-text--scan { margin-top: 28rpx; color: var(--lux-shell-text); font-weight: 500; }
</style>
