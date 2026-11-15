import request from './request'
import type { SysMenu, SysMenuVO, PageResult } from '@/types/api'

const BASE = '/admin/sys-menu'

export function getSysMenuList(params?: Record<string, unknown>) {
  return request.get<PageResult<SysMenu>>(`${BASE}/list`, { params })
}

export function getSysMenuById(id: number) {
  return request.get(`${BASE}/${id}`)
}

export function createSysMenu(data: SysMenu) {
  return request.post(BASE, data)
}

export function updateSysMenu(id: number, data: SysMenu) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteSysMenu(id: number) {
  return request.delete(`${BASE}/${id}`)
}

/** 获取所有权限项（menu_type='F'），用于角色权限分配 */
export function getSysMenuPerms() {
  return request.get<SysMenuVO[]>(`${BASE}/perms`)
}

/** 获取完整菜单树，用于角色权限分配 */
export function getSysMenuTree() {
  return request.get<SysMenuVO[]>(`${BASE}/tree`)
}
