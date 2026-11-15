import request from './request'
import type { QmsInspectionItem, PageResult } from '@/types/api'

const BASE = '/admin/qms-inspection-item'

export function getQmsInspectionItemList(params?: Record<string, unknown>) {
  return request.get<PageResult<QmsInspectionItem>>(`${BASE}/list`, { params })
}

export function getQmsInspectionItemById(id: number) {
  return request.get(`${BASE}/${id}`)
}

export function createQmsInspectionItem(data: QmsInspectionItem) {
  return request.post(BASE, data)
}

export function updateQmsInspectionItem(id: number, data: QmsInspectionItem) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteQmsInspectionItem(id: number) {
  return request.delete(`${BASE}/${id}`)
}
