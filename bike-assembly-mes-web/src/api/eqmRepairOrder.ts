import request from './request'
import type { PageResult, EqmRepairOrder } from '@/types/api'

const BASE = '/admin/eqm-repair-order'

export function getEqmRepairOrderList(params?: Record<string, unknown>) {
  return request.get<PageResult<EqmRepairOrder>>(`${BASE}/list`, { params })
}

export function getEqmRepairOrderById(id: number) {
  return request.get(`${BASE}/${id}`)
}

export function createEqmRepairOrder(data: Partial<EqmRepairOrder>) {
  return request.post(BASE, data)
}

export function updateEqmRepairOrder(id: number, data: Partial<EqmRepairOrder>) {
  return request.put(`${BASE}/${id}`, data)
}

export function acceptEqmRepairOrder(id: number) {
  return request.put(`${BASE}/${id}/accept`)
}

export function completeEqmRepairOrder(id: number, data: { resultDesc: string }) {
  return request.put(`${BASE}/${id}/complete`, data)
}

export function deleteEqmRepairOrder(id: number) {
  return request.delete(`${BASE}/${id}`)
}
