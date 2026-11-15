import { useUserStore } from '@/stores/user'

/** 通配权限标识 — 管理员拥有全部权限 */
const WILDCARD = '*:*:*'

/**
 * 判断当前用户是否拥有指定权限。
 * 未登录或权限未加载时返回 false，保证安全默认值。
 * 管理员（拥有 *:*:* 通配权限）自动返回 true。
 */
export function hasPermission(perm: string): boolean {
  const userStore = useUserStore()
  const perms = userStore.userInfo?.permissions
  if (!perms || perms.length === 0) return false
  // 管理员通配权限
  if (perms.includes(WILDCARD)) return true
  return perms.includes(perm)
}

export function hasAnyPermission(...perms: string[]): boolean {
  return perms.some(p => hasPermission(p))
}

export function hasAllPermissions(...perms: string[]): boolean {
  return perms.every(p => hasPermission(p))
}

/**
 * 判断当前用户是否拥有全部数据范围权限。
 * 未登录或 dataScope 未加载时返回 false。
 */
export function hasAllDataScope(): boolean {
  const userStore = useUserStore()
  return userStore.userInfo?.dataScope === 'ALL'
}
