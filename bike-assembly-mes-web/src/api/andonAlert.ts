import request from './request'
import type { AndonAlert, PageResult } from '@/types/api'

const BASE = '/admin/andon-alert'

export function getAndonAlertList(params?: Record<string, unknown>) {
  return request.get<PageResult<AndonAlert>>(`${BASE}/list`, { params })  // V2.3 分页
}

export function getAndonAlertById(id: number) {
  return request.get(`${BASE}/${id}`)
}

export function createAndonAlert(data: AndonAlert) {
  return request.post(BASE, data)
}

export function updateAndonAlert(id: number, data: AndonAlert) {
  return request.put(`${BASE}/${id}`, data)
}

export function acceptAndonAlert(id: number) {
  return request.put(`${BASE}/${id}/accept`)
}

export function closeAndonAlert(id: number) {
  return request.put(`${BASE}/${id}/close`)
}

export function escalateAndonAlert(id: number, data?: { remark?: string }) {
  return request.put(`${BASE}/${id}/escalate`, data ?? {})
}

export function deleteAndonAlert(id: number) {
  return request.delete(`${BASE}/${id}`)
}
