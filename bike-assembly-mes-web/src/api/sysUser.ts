import request from './request'
import type { SysUser, PageResult } from '@/types/api'

const BASE = '/admin/sys-user'

export function getSysUserList(params?: Record<string, unknown>) {
  return request.get<PageResult<SysUser>>(`${BASE}/list`, { params })  // V2.3 分页
}

export function getSysUserById(id: number) {
  return request.get(`${BASE}/${id}`)
}

export function createSysUser(data: SysUser) {
  return request.post(BASE, data)
}

export function updateSysUser(id: number, data: SysUser) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteSysUser(id: number) {
  return request.delete(`${BASE}/${id}`)
}
