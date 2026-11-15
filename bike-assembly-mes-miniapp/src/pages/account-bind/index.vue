<template>
  <view class="bind-page">
    <view class="bind-header">
      <view class="bind-icon-wrap"><image class="bind-icon-img" src="/static/icons/wechat.svg" mode="aspectFit" /></view>
      <text class="bind-title">绑定系统账号</text>
      <text class="bind-subtitle">首次使用请验证已有账号，绑定后沿用该账号的身份与权限</text>
    </view>
    <view class="bind-form">
      <view class="form-item">
        <text class="form-label">系统账号</text>
        <input class="form-input" v-model="existingForm.username" placeholder="请输入已有账号用户名" :maxlength="64" :disabled="submitting" />
      </view>
      <view class="form-item">
        <text class="form-label">密码</text>
        <view class="password-wrapper">
          <input class="form-input" v-model="existingForm.password" type="text" :password="!showExistingPwd" placeholder="请输入密码" :disabled="submitting" />
          <button class="password-toggle" :aria-label="showExistingPwd ? '隐藏密码' : '显示密码'" :disabled="submitting" @tap.stop="showExistingPwd = !showExistingPwd">
            <image class="eye-icon" :src="showExistingPwd ? '/static/icons/eye-open.svg' : '/static/icons/eye-closed.svg'" mode="aspectFit" />
          </button>
        </view>
      </view>
      <button class="bind-btn" :loading="submitting" :disabled="submitting" @click="handleBindExisting">绑定并登录</button>
      <text class="form-hint">账号身份由系统管理员分配。绑定不会修改账号身份，后续可使用微信一键登录。</text>
    </view>
    <view class="bind-footer"><text class="footer-text" @click="handleBackToLogin">返回登录页</text></view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { wxBindApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const submitting = ref(false)
const showExistingPwd = ref(false)
const existingForm = reactive({ username: '', password: '' })

onShow(() => {
  showExistingPwd.value = false
  if (!uni.getStorageSync('wxTempToken')) {
    uni.showToast({ title: '绑定凭证已失效，请重新微信登录', icon: 'none' })
    uni.reLaunch({ url: '/pages/login/index' })
  }
})

async function handleBindExisting(): Promise<void> {
  if (submitting.value) return
  const tempToken = uni.getStorageSync('wxTempToken')
  if (!tempToken) {
    uni.showToast({ title: '绑定凭证已失效，请重新微信登录', icon: 'none' })
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  if (!existingForm.username.trim() || !existingForm.password) {
    uni.showToast({ title: '请输入系统账号和密码', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const res = await wxBindApi({
      bindType: 'existing',
      username: existingForm.username.trim(),
      password: existingForm.password,
    }, tempToken)
    userStore.login({
      id: res.userId, username: res.username, nickname: res.nickname, roleId: res.roleId,
      token: res.accessToken || res.token, accessToken: res.accessToken,
      refreshToken: res.refreshToken, expiresIn: res.expiresIn, refreshExpiresIn: res.refreshExpiresIn,
      permissions: res.permissions, dataScope: res.dataScope,
      mustChangePassword: res.mustChangePassword, passwordExpired: res.passwordExpired,
    })
    uni.removeStorageSync('wxTempToken')
    uni.removeStorageSync('wxUserId')
    uni.removeStorageSync('wxAvailableRoles')
    existingForm.password = ''
    if (res.mustChangePassword === 1 || res.passwordExpired === true) {
      uni.reLaunch({ url: '/pages/security-block/index' })
      return
    }
    try {
      await userStore.refreshProfile(res.accessToken || res.token)
    } catch {
      // 绑定响应已带身份权限；临时网络问题不阻止进入首页。
      if (!userStore.getAccessToken()) {
        userStore.logout()
        uni.reLaunch({ url: '/pages/login/index' })
        return
      }
    }
    uni.showToast({ title: '绑定成功', icon: 'success' })
    uni.reLaunch({ url: '/pages/home/index' })
  } catch {
    // 请求层展示错误；绑定失败保留临时凭证和表单，允许重试。
  } finally {
    submitting.value = false
  }
}

function handleBackToLogin(): void {
  if (submitting.value) return
  uni.removeStorageSync('wxTempToken')
  uni.removeStorageSync('wxUserId')
  uni.removeStorageSync('wxAvailableRoles')
  uni.reLaunch({ url: '/pages/login/index' })
}
</script>

<style scoped>
.bind-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-height: 100vh;
  background: var(--lux-porcelain-100);
  padding: 60rpx 40rpx 0;
}

/* ========== 头部 ========== */
.bind-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 40rpx;
}

.bind-icon-wrap {
  width: 100rpx;
  height: 100rpx;
  border-radius: var(--lux-radius-sm);
  background: var(--lux-jade-50);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24rpx;
}

.bind-icon-img {
  width: 56rpx;
  height: 56rpx;
}

.bind-title {
  font-size: 36rpx;
  font-weight: 600;
  color: var(--lux-ink-900);
}

.bind-subtitle {
  font-size: 26rpx;
  color: var(--lux-text-faint);
  margin-top: 12rpx;
  text-align: center;
  line-height: 1.5;
}

/* ========== Tab 切换 ========== */
.bind-tabs {
  width: 100%;
  display: flex;
  background: var(--lux-porcelain-200);
  border-radius: 12rpx;
  padding: 6rpx;
  margin-bottom: 32rpx;
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 18rpx 0;
  font-size: 28rpx;
  font-weight: 500;
  color: var(--lux-text);
  border-radius: 10rpx;
  transition: all 0.2s ease;
}

.tab-item--active {
  background: var(--lux-surface);
  color: var(--lux-gold-600);
  font-weight: 600;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.06);
}

/* ========== 表单卡片 ========== */
.bind-form {
  width: 100%;
  background: var(--lux-surface);
  border-radius: var(--lux-radius-md);
  padding: 40rpx 36rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.04);
}

.form-item {
  margin-bottom: 28rpx;
}

.form-label {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--lux-ink-900);
  margin-bottom: 14rpx;
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
  border: 2rpx solid transparent;
  transition: all 0.2s ease;
}

.form-input:focus {
  border-color: var(--lux-gold-600);
  background: var(--lux-surface);
}

.password-wrapper {
  position: relative;
  width: 100%;
}

.password-wrapper .form-input {
  padding-right: 80rpx;
}

.password-toggle {
  margin: 0;
  border: none;
  background: transparent;
  position: absolute;
  right: 8rpx;
  top: 50%;
  transform: translateY(-50%);
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
}

.eye-icon {
  width: 36rpx;
  height: 36rpx;
  opacity: 0.4;
}

.form-picker {
  width: 100%;
  height: 88rpx;
  background: var(--lux-surface-soft);
  border-radius: var(--lux-radius-sm);
  padding: 0 24rpx;
  font-size: 28rpx;
  color: var(--lux-ink-900);
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-sizing: border-box;
}

.picker-placeholder {
  color: var(--lux-text-faint);
}

.picker-arrow {
  font-size: 40rpx;
  color: var(--lux-text-faint);
  font-weight: 300;
  transform: rotate(90deg);
}

/* ========== 按钮 ========== */
.bind-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: var(--lux-gold-gradient);
  color: #17130B;
  font-size: 30rpx;
  font-weight: 600;
  border-radius: var(--lux-radius-sm);
  border: none;
  margin-top: 16rpx;
}

.bind-btn:active {
  opacity: 0.85;
}

.form-hint {
  display: block;
  font-size: 24rpx;
  color: var(--lux-text-faint);
  text-align: center;
  margin-top: 24rpx;
  line-height: 1.6;
}

/* ========== 底部 ========== */
.bind-footer {
  margin-top: 48rpx;
  padding-bottom: 60rpx;
}

.footer-text {
  font-size: 28rpx;
  color: var(--lux-gold-700);
}
</style>
