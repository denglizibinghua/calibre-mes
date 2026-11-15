import request from './request'
import type { SysOperLog, PageResult } from '@/types/api'

const BASE = '/admin/sys-oper-log'

export function getSysOperLogList(params?: Record<string, unknown>) {
  return request.get<{ data: PageResult<SysOperLog> }>(`${BASE}/list`, { params })
}

export function getSysOperLogById(id: number) {
  return request.get(`${BASE}/${id}`)
}

export function createSysOperLog(data: SysOperLog) {
  return request.post(BASE, data)
}

export function updateSysOperLog(id: number, data: SysOperLog) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteSysOperLog(id: number) {
  return request.delete(`${BASE}/${id}`)
}
