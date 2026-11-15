import request from './request'
import type { BaseItem, PageResult } from '@/types/api'

const BASE = '/admin/base-item'

export function getBaseItemList(params?: Record<string, unknown>) {
  return request.get<PageResult<BaseItem>>(`${BASE}/list`, { params })
}

export function getBaseItemById(id: number) {
  return request.get(`${BASE}/${id}`)
}

export function createBaseItem(data: BaseItem) {
  return request.post(BASE, data)
}

export function updateBaseItem(id: number, data: BaseItem) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteBaseItem(id: number) {
  return request.delete(`${BASE}/${id}`)
}
