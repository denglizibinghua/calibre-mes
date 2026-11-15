import request from './request'
import type { BaseRoute, PageResult } from '@/types/api'

const BASE = '/admin/base-route'

export function getBaseRouteList(params?: Record<string, unknown>) {
  return request.get<PageResult<BaseRoute>>(`${BASE}/list`, { params })
}

export function getBaseRouteById(id: number) {
  return request.get(`${BASE}/${id}`)
}

export function createBaseRoute(data: BaseRoute) {
  return request.post(BASE, data)
}

export function updateBaseRoute(id: number, data: BaseRoute) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteBaseRoute(id: number) {
  return request.delete(`${BASE}/${id}`)
}
