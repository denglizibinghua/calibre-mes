import request from './request'
import type { ErpSyncLog, PageResult } from '@/types/api'

const BASE = '/admin/erp-sync-log'

export function getErpSyncLogList(params?: Record<string, unknown>) {
  return request.get<PageResult<ErpSyncLog>>(`${BASE}/list`, { params })  // V2.3 分页
}

export function getErpSyncLogById(id: number) {
  return request.get(`${BASE}/${id}`)
}

export function createErpSyncLog(data: ErpSyncLog) {
  return request.post(BASE, data)
}

export function updateErpSyncLog(id: number, data: ErpSyncLog) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteErpSyncLog(id: number) {
  return request.delete(`${BASE}/${id}`)
}

export function retryErpSyncLog(id: number) {
  return request.post(`${BASE}/${id}/retry`)
}
