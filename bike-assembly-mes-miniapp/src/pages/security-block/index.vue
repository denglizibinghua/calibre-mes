<template>
  <view class="security-block-page">
    <view class="block-card">
      <!-- 图标 -->
      <view class="icon-wrap">
        <image class="icon-img" src="/static/icons/lock.svg" mode="aspectFit" />
      </view>

      <!-- 标题 -->
      <text class="block-title">{{ titleText }}</text>

      <!-- 说明 -->
      <text class="block-desc">{{ descText }}</text>

      <!-- 规则说明 -->
      <view class="rules-card">
        <text class="rules-title">密码要求</text>
        <view class="rule-item">
          <text class="rule-dot">•</text>
          <text class="rule-text">长度 8-64 位</text>
        </view>
        <view class="rule-item">
          <text class="rule-dot">•</text>
          <text class="rule-text">包含数字、大小写字母和特殊字符</text>
        </view>
        <view class="rule-item">
          <text class="rule-dot">•</text>
          <text class="rule-text">不能与近期使用过的密码相同</text>
        </view>
      </view>

      <!-- 退出按钮 -->
      <button class="logout-btn" @click="handleLogout">
        退出登录
      </button>

      <text class="hint-text">修改密码后请重新登录小程序</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'
import { appLogoutApi } from '@/api/auth'

const userStore = useUserStore()

const mustChange = ref(false)
const expired = ref(false)

const titleText = computed(() => {
  if (expired.value) return '密码已过期'
  return '需要修改密码'
})

const descText = computed(() => {
  return '您的账号需要在电脑端管理系统中修改密码后才能继续使用小程序。修改完成后请重新登录。'
})

onShow(() => {
  if (!userStore.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  mustChange.value = userStore.mustChangePassword
  expired.value = userStore.passwordExpired
  // 如果安全状态已清除，说明密码已修改，跳回登录页
  if (!mustChange.value && !expired.value) {
    uni.reLaunch({ url: '/pages/login/index' })
  }
})

async function handleLogout(): Promise<void> {
  try {
    await appLogoutApi()
  } catch {
    // 忽略
  }
  userStore.logout()
  uni.reLaunch({ url: '/pages/login/index' })
}
</script>

<style scoped>
.security-block-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-height: 100vh;
  background: var(--lux-porcelain-100);
  padding: 120rpx 48rpx 0;
  box-sizing: border-box;
}

.block-card {
  width: 100%;
  background: var(--lux-surface);
  border-radius: var(--lux-radius-md);
  padding: 56rpx 40rpx 44rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.04);
}

.icon-wrap {
  width: 108rpx;
  height: 108rpx;
  border-radius: var(--lux-radius-sm);
  background: var(--lux-ember-50);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 28rpx;
}

.icon-img {
  width: 52rpx;
  height: 52rpx;
  opacity: 0.7;
}

.block-title {
  font-size: 36rpx;
  font-weight: 600;
  color: var(--lux-ember-600);
  margin-bottom: 16rpx;
}

.block-desc {
  font-size: 28rpx;
  color: var(--lux-text);
  line-height: 1.6;
  text-align: center;
  margin-bottom: 32rpx;
}

.rules-card {
  width: 100%;
  background: var(--lux-surface-soft);
  border-radius: var(--lux-radius-sm);
  padding: 24rpx 28rpx;
  margin-bottom: 36rpx;
  box-sizing: border-box;
}

.rules-title {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--lux-ink-900);
  margin-bottom: 14rpx;
  display: block;
}

.rule-item {
  display: flex;
  align-items: flex-start;
  margin-bottom: 10rpx;
}

.rule-dot {
  font-size: 24rpx;
  color: var(--lux-text-faint);
  margin-right: 10rpx;
  line-height: 1.6;
}

.rule-text {
  font-size: 24rpx;
  color: var(--lux-text-faint);
  line-height: 1.6;
  flex: 1;
}

.logout-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: var(--lux-wine);
  color: var(--lux-porcelain-50);
  font-size: 30rpx;
  font-weight: 600;
  border-radius: var(--lux-radius-sm);
  border: none;
}

.logout-btn:active {
  opacity: 0.85;
}

.hint-text {
  font-size: 24rpx;
  color: var(--lux-text-faint);
  margin-top: 20rpx;
}
</style>
