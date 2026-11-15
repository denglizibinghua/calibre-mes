import request from './request'
import type { BaseEquipment, PageResult } from '@/types/api'

const BASE = '/admin/base-equipment'

export function getBaseEquipmentList(params?: Record<string, unknown>) {
  return request.get<PageResult<BaseEquipment>>(`${BASE}/list`, { params })
}

export function getBaseEquipmentById(id: number) {
  return request.get(`${BASE}/${id}`)
}

export function createBaseEquipment(data: BaseEquipment) {
  return request.post(BASE, data)
}

export function updateBaseEquipment(id: number, data: BaseEquipment) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteBaseEquipment(id: number) {
  return request.delete(`${BASE}/${id}`)
}
