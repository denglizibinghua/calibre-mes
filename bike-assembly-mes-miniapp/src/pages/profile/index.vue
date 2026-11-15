<template>
  <view class="profile-page">
    <view class="profile-hero">
      <view class="profile-orbit" />
      <view class="page-title-row">
        <view>
          <text class="page-kicker">ACCOUNT CENTER</text>
          <text class="page-title">个人中心</text>
        </view>
        <view class="login-state"><view class="login-state-dot" /><text>已安全登录</text></view>
      </view>

      <view class="user-card">
        <view class="avatar-wrap"><image class="avatar" src="/static/icons/default-avatar.svg" mode="aspectFit" /></view>
        <view class="user-detail">
          <text class="nickname">{{ userStore.nickname || '用户' }}</text>
          <text class="username">{{ userStore.username || '-' }}</text>
          <view class="identity-row">
            <view class="role-tag" v-if="userStore.userInfo?.roleId"><text>{{ roleLabel }}</text></view>
            <view class="scope-tag"><text>{{ userStore.userInfo?.dataScope === 'ALL' ? '全部数据' : '个人数据' }}</text></view>
          </view>
        </view>
      </view>
    </view>

    <view class="menu-section">
      <view class="section-label">账号与系统</view>
      <view class="menu-group">
        <view class="menu-item" hover-class="menu-item--hover" @click="onTapItem('account')">
          <view class="menu-icon-wrap menu-icon-wrap--account"><image class="menu-icon" src="/static/icons/account.svg" mode="aspectFit" /></view>
          <view class="menu-copy"><text class="menu-label">账号信息</text><text class="menu-desc">查看当前登录账号</text></view>
          <view class="menu-right"><text class="menu-value">{{ userStore.username || '-' }}</text><image class="menu-arrow" src="/static/icons/arrow-right.svg" mode="aspectFit" /></view>
        </view>
        <view class="menu-divider" />
        <view class="menu-item" hover-class="menu-item--hover" @click="onTapItem('face')">
          <view class="menu-icon-wrap menu-icon-wrap--privacy"><image class="menu-icon" src="/static/icons/privacy.svg" mode="aspectFit" /></view>
          <view class="menu-copy"><text class="menu-label">人脸登录</text><text class="menu-desc">{{ faceRegistered ? '模板已注册，可重新注册或撤销' : '授权注册加密的人脸模板' }}</text></view>
          <image class="menu-arrow" src="/static/icons/arrow-right.svg" mode="aspectFit" />
        </view>
      </view>

      <view class="section-label section-label--spaced">支持与服务</view>
      <view class="menu-group">
        <view class="menu-item" hover-class="menu-item--hover" @click="onTapItem('help')">
          <view class="menu-icon-wrap menu-icon-wrap--help"><image class="menu-icon" src="/static/icons/help.svg" mode="aspectFit" /></view>
          <view class="menu-copy"><text class="menu-label">使用帮助</text><text class="menu-desc">常见操作与问题说明</text></view>
          <image class="menu-arrow" src="/static/icons/arrow-right.svg" mode="aspectFit" />
        </view>
        <view class="menu-divider" />
        <view class="menu-item" hover-class="menu-item--hover" @click="onTapItem('about')">
          <view class="menu-icon-wrap menu-icon-wrap--about"><image class="menu-icon" src="/static/icons/about.svg" mode="aspectFit" /></view>
          <view class="menu-copy"><text class="menu-label">关于系统</text><text class="menu-desc">自行车装配 MES 移动作业端</text></view>
          <image class="menu-arrow" src="/static/icons/arrow-right.svg" mode="aspectFit" />
        </view>
        <view class="menu-divider" />
        <view class="menu-item" hover-class="menu-item--hover" @click="onTapItem('privacy')">
          <view class="menu-icon-wrap menu-icon-wrap--privacy"><image class="menu-icon" src="/static/icons/privacy.svg" mode="aspectFit" /></view>
          <view class="menu-copy"><text class="menu-label">隐私政策</text><text class="menu-desc">了解数据与隐私保护</text></view>
          <image class="menu-arrow" src="/static/icons/arrow-right.svg" mode="aspectFit" />
        </view>
        <view class="menu-divider" />
        <view class="menu-item" hover-class="menu-item--hover" @click="onTapItem('version')">
          <view class="menu-icon-wrap menu-icon-wrap--version"><image class="menu-icon" src="/static/icons/version.svg" mode="aspectFit" /></view>
          <view class="menu-copy"><text class="menu-label">版本信息</text><text class="menu-desc">当前移动端构建版本</text></view>
          <view class="menu-right"><text class="menu-value">v1.0.0</text><image class="menu-arrow" src="/static/icons/arrow-right.svg" mode="aspectFit" /></view>
        </view>
      </view>
    </view>

    <view class="logout-section"><view class="logout-btn" hover-class="logout-btn--hover" @click="handleLogout"><text class="logout-text">退出当前账号</text></view></view>
    <view class="bottom-placeholder" />
    <TabBar />
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'
import {
  appLogoutApi,
  getFaceTemplateStatusApi,
  registerFaceTemplateApi,
  revokeFaceTemplateApi,
} from '@/api/auth'
import { updateActiveTab } from '@/stores/tabBar'
import TabBar from '@/components/tab-bar/index.vue'

const userStore = useUserStore()
const faceRegistered = ref(false)

/** 角色标签 */
const roleLabel = computed(() => {
  const roleId = userStore.userInfo?.roleId
  if (roleId === 1) return '管理员'
  if (roleId === 2) return '操作员'
  return `角色 ${roleId}`
})

onShow(() => {
  updateActiveTab()
  if (!userStore.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  void refreshFaceStatus()
})

async function refreshFaceStatus(): Promise<void> {
  try {
    faceRegistered.value = await getFaceTemplateStatusApi()
  } catch {
    faceRegistered.value = false
  }
}

/** 菜单项点击 */
function onTapItem(type: string): void {
  if (type === 'face') {
    void handleFaceSettings()
    return
  }
  const titles: Record<string, string> = {
    account: '账号信息',
    help: '使用帮助',
    about: '关于我们',
    privacy: '隐私政策',
    version: '版本信息',
  }
  uni.showToast({ title: `${titles[type] || type} — 暂未开放`, icon: 'none', duration: 1500 })
}

async function handleFaceSettings(): Promise<void> {
  if (faceRegistered.value) {
    const action = await new Promise<number>((resolve) => {
      uni.showActionSheet({
        itemList: ['重新注册', '撤销模板'],
        success: (result) => resolve(result.tapIndex),
        fail: () => resolve(-1),
      })
    })
    if (action === 1) {
      const confirmed = await confirmRevokeFace()
      if (!confirmed) return
      try {
        await revokeFaceTemplateApi()
        faceRegistered.value = false
        uni.showToast({ title: '人脸模板已撤销', icon: 'success' })
      } catch { /* request 层统一提示 */ }
      return
    }
    if (action !== 0) return
  }

  const consent = await confirmFaceConsent(
    '注册时只上传本次摄像头画面用于识别。服务器仅保存加密特征模板，不保存照片。可随时撤销。'
  )
  if (!consent) return
  const imageBase64 = await captureFaceImage()
  if (!imageBase64) return
  try {
    await registerFaceTemplateApi({ imageBase64, consent: true })
    faceRegistered.value = true
    uni.showToast({ title: '人脸模板已注册', icon: 'success' })
  } catch { /* request 层统一提示 */ }
}

function confirmRevokeFace(): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title: '撤销人脸模板',
      content: '撤销后将不能使用人脸登录，账号密码登录仍可使用。',
      confirmText: '确认撤销',
      cancelText: '取消',
      success: (result) => resolve(result.confirm),
      fail: () => resolve(false),
    })
  })
}

function confirmFaceConsent(content: string): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title: '人脸信息授权',
      content,
      confirmText: '同意并继续',
      cancelText: '取消',
      success: (result) => resolve(result.confirm),
      fail: () => resolve(false),
    })
  })
}

function captureFaceImage(): Promise<string | null> {
  return new Promise((resolve) => {
    uni.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['camera'],
      camera: 'front',
      success: (result) => {
        const file = result.tempFiles?.[0]
        if (!file?.tempFilePath) return resolve(null)
        uni.getFileSystemManager().readFile({
          filePath: file.tempFilePath,
          encoding: 'base64',
          success: (readResult) => resolve(String(readResult.data)),
          fail: () => resolve(null),
        })
      },
      fail: () => resolve(null),
    })
  })
}

/** 退出登录 */
async function handleLogout(): Promise<void> {
  const confirmed = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '退出登录',
      content: '确定要退出当前账号吗？',
      confirmText: '退出登录',
      cancelText: '取消',
      confirmColor: '#F53F3F',
      success: (res) => { resolve(res.confirm) },
      fail: () => { resolve(false) },
    })
  })
  if (!confirmed) return

  // 先尝试调用服务端退出接口撤销 Refresh Token
  try {
    await appLogoutApi()
  } catch {
    // 服务端调用失败不影响本地退出
  }

  // 清理本地所有认证数据
  userStore.logout()
  uni.reLaunch({ url: '/pages/login/index' })
}
</script>

<style scoped>
.profile-page { min-height: 100vh; background: var(--lux-porcelain-100); padding-bottom: env(safe-area-inset-bottom); }
.profile-hero { position: relative; padding: calc(env(safe-area-inset-top, 0rpx) + 26rpx) 28rpx 48rpx; overflow: hidden; background: var(--lux-hero-gradient); border-bottom: 1rpx solid var(--lux-shell-line-strong); }
.profile-orbit { position: absolute; width: 330rpx; height: 330rpx; top: -180rpx; right: -100rpx; border: 1rpx solid var(--lux-shell-line); border-radius: var(--lux-radius-round); }
.page-title-row { position: relative; z-index: 2; display: flex; align-items: flex-end; justify-content: space-between; }
.page-title-row > view:first-child { display: flex; flex-direction: column; }
.page-kicker { color: var(--lux-gold-600); font-size: 18rpx; letter-spacing: 3rpx; text-transform: uppercase; }
.page-title { margin-top: 5rpx; color: var(--lux-shell-text); font-size: 36rpx; font-weight: 600; letter-spacing: 1rpx; }
.login-state { display: flex; align-items: center; padding: 7rpx 14rpx; border: 1rpx solid var(--lux-shell-line); border-radius: var(--lux-radius-xs); background: rgba(198, 169, 110, .08); color: var(--lux-shell-text); font-size: 20rpx; letter-spacing: 1rpx; }
.login-state-dot { width: 9rpx; height: 9rpx; margin-right: 8rpx; border-radius: var(--lux-radius-round); background: var(--lux-jade-400); }
.user-card { position: relative; z-index: 2; display: flex; align-items: center; margin-top: 30rpx; padding: 28rpx; border: 1rpx solid var(--lux-shell-line-strong); border-radius: var(--lux-radius-sm); background: var(--lux-ink-800); }
.avatar-wrap { display: flex; align-items: center; justify-content: center; width: 114rpx; height: 114rpx; flex-shrink: 0; border: 1rpx solid var(--lux-shell-line-strong); border-radius: var(--lux-radius-sm); background: rgba(255, 255, 255, .04); }
.avatar { width: 88rpx; height: 88rpx; }
.user-detail { display: flex; min-width: 0; flex: 1; flex-direction: column; margin-left: 22rpx; }
.nickname { overflow: hidden; color: var(--lux-shell-text); font-size: 36rpx; font-weight: 600; letter-spacing: 1rpx; text-overflow: ellipsis; white-space: nowrap; }
.username { margin-top: 5rpx; color: var(--lux-shell-muted); font-size: 23rpx; }
.identity-row { display: flex; align-items: center; margin-top: 14rpx; }
.role-tag, .scope-tag { display: inline-flex; padding: 6rpx 14rpx; border: 1rpx solid var(--lux-shell-line); border-radius: var(--lux-radius-xs); background: rgba(198, 169, 110, .08); color: var(--lux-shell-text); font-size: 20rpx; font-weight: 500; }
.scope-tag { margin-left: 10rpx; }
.menu-section { padding: 28rpx 28rpx 0; }
.section-label { margin: 0 4rpx 14rpx; color: var(--lux-text-muted); font-size: 23rpx; font-weight: 500; letter-spacing: 1rpx; }
.section-label--spaced { margin-top: 26rpx; }
.menu-group { overflow: hidden; border: 1rpx solid var(--lux-line); border-radius: var(--lux-radius-md); background: var(--lux-surface); box-shadow: var(--lux-shadow-card); }
.menu-item { display: flex; align-items: center; min-height: 112rpx; padding: 22rpx 22rpx; transition: background .15s ease; }
.menu-item--hover { background: var(--lux-surface-soft); }
.menu-divider { height: 1rpx; margin-left: 88rpx; background: var(--lux-line-soft); }
.menu-icon-wrap { display: flex; align-items: center; justify-content: center; width: 52rpx; height: 52rpx; flex-shrink: 0; margin-right: 18rpx; border: 1rpx solid var(--lux-line); border-radius: var(--lux-radius-xs); background: var(--lux-surface-soft); }
.menu-icon-wrap--account, .menu-icon-wrap--help, .menu-icon-wrap--about, .menu-icon-wrap--privacy, .menu-icon-wrap--version { background: var(--lux-surface-soft); }
.menu-icon { width: 30rpx; height: 30rpx; }
.menu-copy { display: flex; min-width: 0; flex: 1; flex-direction: column; }
.menu-label { color: var(--lux-ink-900); font-size: 28rpx; font-weight: 500; }
.menu-desc { margin-top: 5rpx; overflow: hidden; color: var(--lux-text-muted); font-size: 21rpx; text-overflow: ellipsis; white-space: nowrap; }
.menu-right { display: flex; align-items: center; max-width: 210rpx; }
.menu-value { margin-right: 7rpx; overflow: hidden; color: var(--lux-text-faint); font-size: 23rpx; text-overflow: ellipsis; white-space: nowrap; }
.menu-arrow { width: 28rpx; height: 28rpx; flex-shrink: 0; opacity: .55; }
.logout-section { padding: 32rpx 28rpx 0; }
.logout-btn { display: flex; width: 100%; height: 92rpx; align-items: center; justify-content: center; border: 1rpx solid var(--lux-wine-line); border-radius: var(--lux-radius-sm); background: var(--lux-surface); }
.logout-btn--hover { background: var(--lux-wine-50); }
.logout-text { color: var(--lux-wine); font-size: 27rpx; font-weight: 500; }
.bottom-placeholder { height: calc(154rpx + env(safe-area-inset-bottom, 0rpx)); }
</style>
