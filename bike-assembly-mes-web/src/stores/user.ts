import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { disconnect } from '@/utils/useWebSocket'
import { repairTextEncoding } from '@/utils/text'

export interface UserInfo {
  id: number
  username: string
  nickname: string
  roleId: number
  token: string
  permissions: string[]
  dataScope: string
  mustChangePassword?: number
  passwordExpired?: boolean
}

/**
 * [前端调用] 用户状态管理（Pinia Store）。
 * 登录时将后端返回的 UserLoginVO 存入此 Store 并持久化到 localStorage。
 * 各组件通过 useUserStore() 获取当前用户信息和权限。
 */
export const useUserStore = defineStore('user', () => {
  // 页面刷新时从 localStorage 恢复用户状态（避免刷新后丢失登录状态）
  const userInfo = ref<UserInfo | null>(loadUserInfo())

  // 以下 computed 属性供组件使用，自动从 userInfo 中提取
  const token = computed(() => userInfo.value?.token || '')
  const nickname = computed(() => repairTextEncoding(userInfo.value?.nickname) || '未登录')
  const username = computed(() => userInfo.value?.username || '')
  const isLoggedIn = computed(() => !!userInfo.value?.token)
  // [权限控制] 权限码列表，供 v-permission 指令使用
  const permissions = computed(() => userInfo.value?.permissions || [])
  // [权限控制] 数据范围，供列表页根据范围过滤数据
  const dataScope = computed(() => userInfo.value?.dataScope || 'SELF')
  // [业务规则] 密码安全状态
  const mustChangePassword = computed(() => userInfo.value?.mustChangePassword === 1)
  const passwordExpired = computed(() => userInfo.value?.passwordExpired === true)

  // 从 localStorage 加载用户信息（JSON 反序列化）
  function loadUserInfo(): UserInfo | null {
    try {
      const raw = localStorage.getItem('userInfo')
      return raw ? JSON.parse(raw) : null
    } catch {
      return null
    }
  }

  // [前端调用] 登录成功后调用：更新 Store + 持久化到 localStorage
  function login(info: UserInfo) {
    disconnect()
    userInfo.value = info
    localStorage.setItem('token', info.token)
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  // [前端调用] 退出登录时调用：清空 Store + 清除 localStorage 中的 Token 和用户信息
  function logout() {
    disconnect()
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('userInfo')
  }

  return { userInfo, token, nickname, username, isLoggedIn, permissions, dataScope,
           mustChangePassword, passwordExpired, login, logout }
})
