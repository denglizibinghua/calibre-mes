import request from './request'
import type { WmsInventory, PageResult } from '@/types/api'

const BASE = '/admin/wms-inventory'

export function getWmsInventoryList(params?: Record<string, unknown>) {
  return request.get<PageResult<WmsInventory>>(`${BASE}/list`, { params })
}

export function getWmsInventoryById(id: number) {
  return request.get<WmsInventory>(`${BASE}/${id}`)
}

export function createWmsInventory(data: WmsInventory) {
  return request.post<WmsInventory>(BASE, data)
}

export function updateWmsInventory(id: number, data: WmsInventory) {
  return request.put<WmsInventory>(`${BASE}/${id}`, data)
}

export function deleteWmsInventory(id: number) {
  return request.delete(`${BASE}/${id}`)
}
