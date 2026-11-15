import request from './request'
import type { SafSafetyEvent, PageResult } from '@/types/api'

const BASE = '/admin/saf-safety-event'

export function getSafSafetyEventList(params?: Record<string, unknown>) {
  return request.get<PageResult<SafSafetyEvent>>(`${BASE}/list`, { params })  // V2.3 分页
}

export function getSafSafetyEventById(id: number) {
  return request.get(`${BASE}/${id}`)
}

export function createSafSafetyEvent(data: SafSafetyEvent) {
  return request.post(BASE, data)
}

export function updateSafSafetyEvent(id: number, data: SafSafetyEvent) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteSafSafetyEvent(id: number) {
  return request.delete(`${BASE}/${id}`)
}
