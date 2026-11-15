import request from './request'
import type { WmsLocation, PageResult } from '@/types/api'

const BASE = '/admin/wms-location'

export function getWmsLocationList(params?: Record<string, unknown>) {
  return request.get<PageResult<WmsLocation>>(`${BASE}/list`, { params })
}

export function getWmsLocationById(id: number) {
  return request.get(`${BASE}/${id}`)
}

export function createWmsLocation(data: WmsLocation) {
  return request.post(BASE, data)
}

export function updateWmsLocation(id: number, data: WmsLocation) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteWmsLocation(id: number) {
  return request.delete(`${BASE}/${id}`)
}
