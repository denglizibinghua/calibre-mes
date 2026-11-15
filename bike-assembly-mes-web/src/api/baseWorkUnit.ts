import request from './request'
import type { BaseWorkUnit, PageResult } from '@/types/api'

const BASE = '/admin/base-work-unit'

export function getBaseWorkUnitList(params?: Record<string, unknown>) {
  return request.get<PageResult<BaseWorkUnit>>(`${BASE}/list`, { params })
}

export function getBaseWorkUnitById(id: number) {
  return request.get(`${BASE}/${id}`)
}

export function createBaseWorkUnit(data: BaseWorkUnit) {
  return request.post(BASE, data)
}

export function updateBaseWorkUnit(id: number, data: BaseWorkUnit) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteBaseWorkUnit(id: number) {
  return request.delete(`${BASE}/${id}`)
}
