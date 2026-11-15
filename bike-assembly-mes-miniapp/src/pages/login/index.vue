<template>
  <view class="login-page">
    <view class="login-hero" :style="{ paddingTop: contentTop + 'px' }">
      <view class="hero-grid" />
      <view class="hero-circle hero-circle--one" />
      <view class="hero-circle hero-circle--two" />
      <view class="brand-area">
        <view class="brand-kicker"><view class="kicker-dot" /><text>MES MOBILE TERMINAL</text></view>
        <view class="brand-main">
          <view class="logo-wrapper"><image class="logo-img" src="/static/logo.png" mode="aspectFit" /></view>
          <view class="brand-copy">
            <text class="app-name">自行车装配 MES</text>
            <text class="app-desc">生产执行系统 · 移动作业端</text>
          </view>
        </view>
        <text class="hero-slogan">让每一道装配工序更清晰、更及时、更可靠</text>
        <view class="capability-row">
          <text>工单执行</text><view class="capability-dot" />
          <text>工序报工</text><view class="capability-dot" />
          <text>质量追溯</text>
        </view>
      </view>
    </view>

    <view class="login-card">
      <view class="card-heading">
        <text class="card-title">欢迎登录</text>
        <text class="card-subtitle">请使用已分配的系统账号进入作业台</text>
      </view>

      <view class="form-item">
        <text class="form-label">账号</text>
        <view class="input-wrapper">
          <image class="input-icon" src="/static/icons/profile.svg" mode="aspectFit" />
          <input class="form-input" v-model="form.username" placeholder="请输入用户名" placeholder-style="color: #6F6A5F" :disabled="submitting" />
        </view>
      </view>

      <view class="form-item">
        <text class="form-label">密码</text>
        <view class="input-wrapper">
          <image class="input-icon" src="/static/icons/lock.svg" mode="aspectFit" />
          <input class="form-input" v-model="form.password" type="text" :password="!showPassword" placeholder="请输入密码" placeholder-style="color: #6F6A5F" :disabled="submitting" />
          <button class="password-eye" :aria-label="showPassword ? '隐藏密码' : '显示密码'" :disabled="submitting" @tap.stop="showPassword = !showPassword">
            <image class="eye-icon" :src="showPassword ? '/static/icons/eye-open.svg' : '/static/icons/eye-closed.svg'" mode="aspectFit" />
          </button>
        </view>
      </view>

      <button class="login-btn" :class="{ 'login-btn--loading': submitting }" :loading="submitting" :disabled="submitting" @click="handleLogin">
        {{ submitting ? '正在安全登录...' : '登录作业台' }}
      </button>

      <button class="face-btn" :disabled="submitting" @click="handleFaceLogin">
        使用人脸登录
      </button>

      <view class="divider"><view class="divider-line" /><text class="divider-text">或使用快捷登录</text><view class="divider-line" /></view>

      <button class="wechat-btn" :loading="submitting" :disabled="submitting" @click="handleWxLogin">
        <image class="wechat-icon" src="/static/icons/wechat.svg" mode="aspectFit" />
        微信一键登录
      </button>
    </view>

    <view class="security-tip"><view class="security-dot" /><text>登录信息将通过安全通道传输</text></view>
    <view class="login-footer">
      <text class="footer-text">登录即表示同意</text><text class="footer-link">《用户服务协议》</text>
      <text class="footer-text">和</text><text class="footer-link">《隐私政策》</text>
    </view>
    <view class="version-info"><text class="version-text">Bike Assembly MES · Mobile v1.0</text></view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { appLoginApi, faceLoginApi, wxLoginApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import type { LoginRequest, LoginResponse, WxLoginResponse } from '@/types/api'

import { getCustomContentTop } from '@/utils/customNavigation'
import { captureFaceImage } from '@/utils/faceCapture'

const contentTop = ref(getCustomContentTop())
const userStore = useUserStore()
const submitting = ref(false)
const showPassword = ref(false)

const form = reactive<LoginRequest>({
  username: '',
  password: '',
})

/** 已登录用户直接跳转首页 */
onShow(async () => {
  contentTop.value = getCustomContentTop()
  showPassword.value = false
  if (userStore.isLoggedIn) {
    try {
      await userStore.refreshProfile()
      uni.reLaunch({ url: '/pages/home/index' })
    } catch {
      userStore.logout()
    }
  }
})

async function handleLogin(): Promise<void> {
  if (submitting.value) return
  if (!form.username.trim()) {
    uni.showToast({ title: '请输入用户名', icon: 'none' })
    return
  }
  if (!form.password) {
    uni.showToast({ title: '请输入密码', icon: 'none' })
    return
  }

  submitting.value = true
  try {
    const data = await appLoginApi({
      username: form.username.trim(),
      password: form.password,
    })

    await completeLogin(data)
  } catch {
    // 错误信息已由 request 层统一展示
  } finally {
    submitting.value = false
  }
}

async function completeLogin(data: LoginResponse): Promise<void> {
  const userInfo = {
      id: data.id,
      username: data.username,
      nickname: data.nickname,
      roleId: data.roleId,
      token: data.accessToken || data.token,
      accessToken: data.accessToken,
      refreshToken: data.refreshToken,
      expiresIn: data.expiresIn,
      refreshExpiresIn: data.refreshExpiresIn,
      permissions: data.permissions,
      dataScope: data.dataScope,
      mustChangePassword: data.mustChangePassword,
      passwordExpired: data.passwordExpired,
    }
  userStore.login(userInfo)

  try {
    await userStore.refreshProfile(data.accessToken || data.token)
  } catch {
    // 权限刷新失败不阻塞登录
  }

  if (data.mustChangePassword === 1 || data.passwordExpired === true) {
    uni.setStorageSync('securityMustChange', data.mustChangePassword === 1)
    uni.setStorageSync('securityPwdExpired', data.passwordExpired === true)
    uni.reLaunch({ url: '/pages/security-block/index' })
    return
  }
  uni.showToast({ title: '登录成功', icon: 'success', duration: 1000 })
  setTimeout(() => {
    uni.reLaunch({ url: '/pages/home/index' })
  }, 1000)
}

async function handleFaceLogin(): Promise<void> {
  if (submitting.value) return
  submitting.value = true
  try {
    const imageBase64 = await captureFaceImage()
    if (!imageBase64) return
    const username = form.username.trim()
    const data = await faceLoginApi({ username: username || undefined, imageBase64 })
    await completeLogin(data)
  } catch {
    // 错误信息已由 request 层统一展示
  } finally {
    submitting.value = false
  }
}

/** 微信一键登录 */
async function handleWxLogin(): Promise<void> {
  if (submitting.value) return
  // #ifndef MP-WEIXIN
  uni.showToast({ title: '请在微信小程序中使用微信登录', icon: 'none' })
  return;
  // #endif
  submitting.value = true
  try {
    // 1. 调用微信登录获取临时 code
    let loginRes: UniApp.LoginRes
    try {
      loginRes = await uni.login({ provider: 'weixin', timeout: 10000 })
    } catch (loginErr: any) {
      console.error('[wx-login] uni.login 失败:', loginErr)
      uni.showToast({ title: '微信登录初始化失败，请检查小程序 AppID 配置', icon: 'none', duration: 3000 })
      return
    }

    if (!loginRes.code) {
      console.error('[wx-login] uni.login 返回的 code 为空')
      uni.showToast({ title: '获取微信登录凭证失败，请重试', icon: 'none' })
      return
    }

    // 2. 调用后端 wx-login 接口
    const res: WxLoginResponse = await wxLoginApi({ code: loginRes.code })

    if (res.status === 1) {
      userStore.login({
        id: res.userId!,
        username: res.username!,
        nickname: res.nickname!,
        roleId: res.roleId!,
        token: res.accessToken || res.token!,
        accessToken: res.accessToken,
        refreshToken: res.refreshToken,
        expiresIn: res.expiresIn,
        refreshExpiresIn: res.refreshExpiresIn,
        permissions: res.userInfo?.permissions || res.permissions,
        dataScope: res.userInfo?.dataScope || res.dataScope,
        mustChangePassword: res.mustChangePassword,
        passwordExpired: res.passwordExpired,
      })
      try {
        await userStore.refreshProfile(res.accessToken || res.token)
      } catch {
        // 权限刷新失败不阻塞登录
      }

      // 强制改密或密码过期：跳转阻断页
      if (res.mustChangePassword === 1 || res.passwordExpired === true) {
        uni.setStorageSync('securityMustChange', res.mustChangePassword === 1)
        uni.setStorageSync('securityPwdExpired', res.passwordExpired === true)
        uni.reLaunch({ url: '/pages/security-block/index' })
        return
      }

      uni.showToast({ title: '登录成功', icon: 'success', duration: 1000 })
      setTimeout(() => {
        uni.reLaunch({ url: '/pages/home/index' })
      }, 1000)
    } else if ((res.status === 2 || res.status === 3) && res.tempToken) {
      userStore.logout()
      uni.setStorageSync('wxTempToken', res.tempToken)
      uni.setStorageSync('wxUserId', res.userId)
      uni.setStorageSync('wxAvailableRoles', JSON.stringify(res.availableRoles || []))
      uni.navigateTo({ url: '/pages/account-bind/index' })
    } else {
      uni.showToast({ title: '微信登录响应不完整，请重新登录', icon: 'none' })
    }
  } catch (err: any) {
    // 这里捕获的是 wxLoginApi（HTTP 请求层）已经展示过 toast 的错误
    // 以及任何未被内层 try/catch 处理的异常
    console.error('[wx-login] 微信登录流程异常:', err?.message || err)
    // 如果 request 层没有展示 toast（例如非 HTTP 错误），这里补一个兜底提示
    if (err?.message && !err.message.includes('请求失败') && !err.message.includes('网络异常')) {
      uni.showToast({ title: err.message || '微信登录失败，请稍后重试', icon: 'none', duration: 2500 })
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.login-page { display: flex; min-height: 100vh; flex-direction: column; align-items: center; background: var(--lux-porcelain-100); padding-bottom: env(safe-area-inset-bottom); }
.login-hero::before { content: ''; position: absolute; top: 24rpx; left: 24rpx; width: 28rpx; height: 28rpx; border-top: 1rpx solid var(--lux-gold-500); border-left: 1rpx solid var(--lux-gold-500); pointer-events: none; }
.login-hero { position: relative; width: 100%; min-height: 560rpx; padding: calc(env(safe-area-inset-top, 0rpx) + 54rpx) 44rpx 150rpx; overflow: hidden; background: var(--lux-hero-gradient); border-bottom: 1rpx solid var(--lux-shell-line-strong); }
.hero-grid { display: none; }
.hero-circle { position: absolute; border: 1rpx solid var(--lux-shell-line); border-radius: var(--lux-radius-round); }
.hero-circle--one { width: 320rpx; height: 320rpx; top: -180rpx; right: -100rpx; }
.hero-circle--two { width: 180rpx; height: 180rpx; right: 20rpx; bottom: -90rpx; }
.brand-area { position: relative; z-index: 2; }
.brand-kicker { display: inline-flex; align-items: center; padding: 8rpx 14rpx; border: 1rpx solid var(--lux-shell-line-strong); border-radius: var(--lux-radius-xs); background: transparent; color: var(--lux-gold-600); font-size: 18rpx; letter-spacing: 2rpx; text-transform: uppercase; }
.kicker-dot { width: 8rpx; height: 8rpx; margin-right: 9rpx; border-radius: var(--lux-radius-round); background: var(--lux-gold-500); box-shadow: 0 0 0 5rpx rgba(198, 169, 110, .12); }
.brand-main { display: flex; align-items: center; margin-top: 36rpx; }
.logo-wrapper { display: flex; align-items: center; justify-content: center; width: 104rpx; height: 104rpx; flex-shrink: 0; border: 1rpx solid var(--lux-shell-line-strong); border-radius: var(--lux-radius-sm); background: rgba(255, 255, 255, .04); box-shadow: inset 0 1rpx 0 var(--lux-shell-line); }
.logo-img { width: 64rpx; height: 64rpx; }
.brand-copy { display: flex; flex-direction: column; margin-left: 22rpx; }
.app-name { color: var(--lux-shell-text); font-size: 40rpx; font-weight: 600; letter-spacing: 1rpx; }
.app-desc { margin-top: 7rpx; color: var(--lux-shell-muted); font-size: 23rpx; letter-spacing: 1rpx; }
.hero-slogan { display: block; max-width: 590rpx; margin-top: 34rpx; color: var(--lux-shell-text); font-size: 27rpx; font-weight: 400; line-height: 1.6; }
.capability-row { display: flex; align-items: center; margin-top: 24rpx; color: var(--lux-shell-muted); font-size: 20rpx; letter-spacing: 1rpx; }
.capability-dot { width: 5rpx; height: 5rpx; margin: 0 13rpx; border-radius: var(--lux-radius-round); background: var(--lux-gold-500); }
.login-card::before { content: ''; position: absolute; top: 14rpx; left: 14rpx; width: 20rpx; height: 20rpx; border-top: 1rpx solid var(--lux-gold-500); border-left: 1rpx solid var(--lux-gold-500); pointer-events: none; }
.login-card { position: relative; z-index: 3; width: calc(100% - 56rpx); margin-top: -104rpx; padding: 38rpx 32rpx 32rpx; border: 1rpx solid var(--lux-line); border-radius: var(--lux-radius-md); background: var(--lux-surface); box-shadow: var(--lux-shadow-card); }
.card-heading { display: flex; flex-direction: column; margin-bottom: 30rpx; }
.card-title { color: var(--lux-ink-900); font-size: 36rpx; font-weight: 600; letter-spacing: 1rpx; }
.card-subtitle { margin-top: 7rpx; color: var(--lux-text-muted); font-size: 23rpx; }
.form-item { margin-bottom: 24rpx; }
.form-label { display: block; margin: 0 0 10rpx 4rpx; color: var(--lux-text); font-size: 24rpx; font-weight: 500; }
.input-wrapper { display: flex; align-items: center; overflow: hidden; border: 1rpx solid var(--lux-line-strong); border-radius: var(--lux-radius-sm); background: var(--lux-surface-soft); transition: all .2s ease; }
.input-wrapper:focus-within { border-color: var(--lux-gold-500); background: var(--lux-surface); box-shadow: 0 0 0 4rpx rgba(198, 169, 110, .16); }
.input-icon { width: 34rpx; height: 34rpx; margin-left: 24rpx; flex-shrink: 0; opacity: .55; }
.form-input { flex: 1; min-width: 0; height: 94rpx; padding: 0 20rpx; background: transparent; color: var(--lux-ink-900); font-size: 29rpx; }
.password-eye { display: flex; align-items: center; justify-content: center; width: 88rpx; height: 94rpx; flex-shrink: 0; margin: 0; padding: 0; border: none; border-radius: 0; background: transparent; }
.eye-icon { width: 34rpx; height: 34rpx; opacity: .55; }
.login-btn { width: 100%; height: 94rpx; margin-top: 8rpx; border: 1rpx solid var(--lux-gold-600); border-radius: var(--lux-radius-sm); background: var(--lux-gold-gradient); box-shadow: 0 1rpx 0 rgba(255, 255, 255, .35) inset, 0 8rpx 18rpx rgba(120, 94, 44, .18); color: #17130B; font-size: 30rpx; font-weight: 600; letter-spacing: 1rpx; line-height: 94rpx; transition: all .18s ease; }
.login-btn:active { transform: scale(.985); opacity: .9; }
.login-btn--loading { opacity: .72; }
.face-btn { width: 100%; height: 82rpx; margin-top: 18rpx; border: 1rpx solid var(--lux-line); border-radius: var(--lux-radius-sm); background: var(--lux-surface); color: var(--lux-gold-700); font-size: 27rpx; font-weight: 500; line-height: 80rpx; }
.face-btn[disabled] { opacity: .55; }
.divider { display: flex; align-items: center; margin: 30rpx 0 24rpx; }
.divider-line { flex: 1; height: 1rpx; background: var(--lux-line); }
.divider-text { padding: 0 20rpx; color: var(--lux-text-muted); font-size: 21rpx; letter-spacing: 1rpx; }
.wechat-btn { display: flex; width: 100%; height: 90rpx; align-items: center; justify-content: center; border: 1rpx solid var(--lux-line); border-radius: var(--lux-radius-sm); background: var(--lux-surface); color: var(--lux-text); font-size: 28rpx; font-weight: 500; line-height: 90rpx; }
.wechat-btn:active { background: var(--lux-surface-soft); }
.wechat-icon { width: 36rpx; height: 36rpx; margin-right: 12rpx; }
.security-tip { display: flex; align-items: center; margin-top: 28rpx; color: var(--lux-text-muted); font-size: 21rpx; }
.security-dot { width: 9rpx; height: 9rpx; margin-right: 9rpx; border-radius: var(--lux-radius-round); background: var(--lux-jade); }
.login-footer { display: flex; align-items: center; justify-content: center; flex-wrap: wrap; margin-top: 20rpx; padding: 0 36rpx; }
.footer-text, .footer-link { font-size: 21rpx; }
.footer-text { color: var(--lux-text-faint); }
.footer-link { color: var(--lux-gold-700); }
.version-info { margin: 14rpx 0 42rpx; }
.version-text { color: var(--lux-text-faint); font-size: 20rpx; letter-spacing: 1rpx; }
</style>
