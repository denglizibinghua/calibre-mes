import request from './request'
import type { SysConfig, PageResult } from '@/types/api'

const BASE = '/admin/sys-config'

export function getSysConfigList(params?: Record<string, unknown>) {
  return request.get<PageResult<SysConfig>>(`${BASE}/list`, { params })  // V2.3 分页
}

export function getSysConfigById(id: number) {
  return request.get(`${BASE}/${id}`)
}

export function createSysConfig(data: SysConfig) {
  return request.post(BASE, data)
}

export function updateSysConfig(id: number, data: SysConfig) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteSysConfig(id: number) {
  return request.delete(`${BASE}/${id}`)
}
