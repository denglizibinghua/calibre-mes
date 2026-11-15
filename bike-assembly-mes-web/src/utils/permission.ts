import { usePermissionStore } from '@/stores/permission'

/**
 * 检查是否有指定权限。
 * 使用 permissionStore 进行 O(1) 查询。
 */
export function hasPermission(perm: string): boolean {
  const store = usePermissionStore()
  return store.hasPermission(perm)
}

/**
 * 检查是否有任一权限。
 */
export function hasAnyPermission(...perms: string[]): boolean {
  const store = usePermissionStore()
  return store.hasAnyPermission(...perms)
}

/**
 * 检查是否有全部权限。
 */
export function hasAllPermissions(...perms: string[]): boolean {
  const store = usePermissionStore()
  return store.hasAllPermissions(...perms)
}
