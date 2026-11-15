import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { STORAGE_KEYS } from '@/constants'
import type { UserInfo } from '@/types/api'
import { getUserProfile } from '@/api/auth'
import { closeWs } from '@/utils/websocket'

/**
 * 用户状态 Store — V3.3 双 Token 支持。
 *
 * 维护双 Token、当前用户基本信息、登录状态。
 * 支持从本地存储恢复登录状态，以及登录/退出操作。
 * 不将明文密码保存到本地。
 */
export const useUserStore = defineStore('user', () => {
  const userInfo = ref<UserInfo | null>(loadUserInfo())

  const token = computed(() => userInfo.value?.token || '')
  const nickname = computed(() => userInfo.value?.nickname || '未登录')
  const username = computed(() => userInfo.value?.username || '')
  const isLoggedIn = computed(() => !!userInfo.value?.token)
  const mustChangePassword = computed(() => userInfo.value?.mustChangePassword === 1)
  const passwordExpired = computed(() => userInfo.value?.passwordExpired === true)

  /**
   * 从本地存储恢复登录状态。
   */
  function loadUserInfo(): UserInfo | null {
    try {
      const raw = uni.getStorageSync(STORAGE_KEYS.USER_INFO)
      if (raw) {
        const parsed: unknown = JSON.parse(raw)
        const info = parsed as UserInfo
        // 有效性校验：token 从双 Token 获取
        const savedToken = getAccessToken()
        if (info && savedToken && info.id) {
          info.token = savedToken
          return info
        }
      }
      return null
    } catch {
      return null
    }
  }

  // ==================== 双 Token 统一封装 ====================

  /** 统一保存双 Token 到本地存储 */
  function setAuthTokens(accessToken: string, refreshToken: string, expiresIn?: number, refreshExpiresIn?: number): void {
    try {
      uni.setStorageSync(STORAGE_KEYS.ACCESS_TOKEN, accessToken)
      uni.setStorageSync(STORAGE_KEYS.TOKEN, accessToken) // 兼容旧代码
      uni.setStorageSync(STORAGE_KEYS.REFRESH_TOKEN, refreshToken)
      if (expiresIn) {
        uni.setStorageSync(STORAGE_KEYS.ACCESS_TOKEN_EXPIRES_AT, Date.now() + expiresIn * 1000)
      }
      if (refreshExpiresIn) {
        uni.setStorageSync(STORAGE_KEYS.REFRESH_TOKEN_EXPIRES_AT, Date.now() + refreshExpiresIn * 1000)
      }
    } catch {
      // 存储写入失败不影响内存状态
    }
  }

  /** 获取 Access Token */
  function getAccessToken(): string {
    try {
      return uni.getStorageSync(STORAGE_KEYS.ACCESS_TOKEN) || uni.getStorageSync(STORAGE_KEYS.TOKEN) || ''
    } catch {
      return ''
    }
  }

  /** 获取 Refresh Token */
  function getRefreshToken(): string {
    try {
      return uni.getStorageSync(STORAGE_KEYS.REFRESH_TOKEN) || ''
    } catch {
      return ''
    }
  }

  /** 统一清理所有认证数据 */
  function clearAuthTokens(): void {
    closeWs()
    try {
      uni.removeStorageSync(STORAGE_KEYS.ACCESS_TOKEN)
      uni.removeStorageSync(STORAGE_KEYS.TOKEN)
      uni.removeStorageSync(STORAGE_KEYS.REFRESH_TOKEN)
      uni.removeStorageSync(STORAGE_KEYS.ACCESS_TOKEN_EXPIRES_AT)
      uni.removeStorageSync(STORAGE_KEYS.REFRESH_TOKEN_EXPIRES_AT)
      uni.removeStorageSync(STORAGE_KEYS.USER_INFO)
    } catch {
      // 存储清理失败不影响内存状态
    }
  }

  /**
   * 登录成功后保存状态到 Store 和本地存储。
   */
  function login(info: UserInfo): void {
    closeWs()
    userInfo.value = info
    // 保存双 Token
    const at = info.accessToken || info.token
    const rt = info.refreshToken || ''
    setAuthTokens(at, rt, info.expiresIn, info.refreshExpiresIn)
    // 同时确保 userInfo 中的 token 是 accessToken
    if (info.accessToken) {
      info.token = info.accessToken
    }
    persistUserInfo()
  }

  /**
   * 从远端刷新用户档案信息（权限、角色等）。
   * 失败时抛出异常供调用方处理。
   * @param tokenOverride 可选，直接传入 token 绕过 storage 读取
   */
  async function refreshProfile(tokenOverride?: string): Promise<void> {
    const profile = await getUserProfile(tokenOverride)
    if (userInfo.value) {
      userInfo.value = {
        ...userInfo.value,
        permissions: profile.permissions ?? [],
        dataScope: profile.dataScope ?? 'SELF',
        mustChangePassword: profile.mustChangePassword,
        passwordExpired: profile.passwordExpired,
      }
      persistUserInfo()
    }
  }

  /**
   * 手动设置权限信息。
   */
  function setPermissions(permissions: string[], dataScope: string): void {
    if (userInfo.value) {
      userInfo.value = {
        ...userInfo.value,
        permissions,
        dataScope,
      }
      persistUserInfo()
    }
  }

  /**
   * 持久化当前 userInfo 到本地存储。
   */
  function persistUserInfo(): void {
    try {
      if (userInfo.value) {
        uni.setStorageSync(STORAGE_KEYS.USER_INFO, JSON.stringify(userInfo.value))
      }
    } catch {
      // 存储写入失败不影响内存状态
    }
  }

  /**
   * 退出登录：清空 Store 和本地存储。
   */
  function logout(): void {
    userInfo.value = null
    clearAuthTokens()
  }

  return {
    userInfo, token, nickname, username, isLoggedIn,
    mustChangePassword, passwordExpired,
    login, logout, setPermissions, refreshProfile,
    setAuthTokens, getAccessToken, getRefreshToken, clearAuthTokens,
  }
})
