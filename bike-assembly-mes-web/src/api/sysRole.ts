import request from './request'
import type { SysRole, PageResult } from '@/types/api'

const BASE = '/admin/sys-role'

export function getSysRoleList(params?: Record<string, unknown>) {
  return request.get<PageResult<SysRole>>(`${BASE}/list`, { params })
}

export function getSysRoleById(id: number) {
  return request.get(`${BASE}/${id}`)
}

export function createSysRole(data: SysRole & { menuIds?: number[] }) {
  return request.post(BASE, data)
}

export function updateSysRole(id: number, data: SysRole & { menuIds?: number[] }) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteSysRole(id: number) {
  return request.delete(`${BASE}/${id}`)
}

/** 获取角色已分配的菜单 ID 列表 */
export function getRoleMenuIds(id: number) {
  return request.get<number[]>(`${BASE}/${id}/menus`)
}

/** 分配菜单权限给角色 */
export function assignRoleMenus(id: number, menuIds: number[]) {
  return request.put(`${BASE}/${id}/menus`, menuIds)
}
