import request from './request'
import type { PageResult, EqmMaintenance } from '@/types/api'

const BASE = '/admin/eqm-maintenance'

export function getEqmMaintenanceList(params?: Record<string, unknown>) {
  return request.get<PageResult<EqmMaintenance>>(`${BASE}/list`, { params })
}

export function getEqmMaintenanceById(id: number) {
  return request.get(`${BASE}/${id}`)
}

export function createEqmMaintenance(data: EqmMaintenance) {
  return request.post(BASE, data)
}

export function updateEqmMaintenance(id: number, data: EqmMaintenance) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteEqmMaintenance(id: number) {
  return request.delete(`${BASE}/${id}`)
}
