import request from './request'
import type { QmsDefect, PageResult } from '@/types/api'

const BASE = '/admin/qms-defect'

export function getQmsDefectList(params?: Record<string, unknown>) {
  return request.get<PageResult<QmsDefect>>(`${BASE}/list`, { params })  // V2.3 分页
}

export function getQmsDefectById(id: number) {
  return request.get(`${BASE}/${id}`)
}

export function createQmsDefect(data: QmsDefect) {
  return request.post(BASE, data)
}

export function updateQmsDefect(id: number, data: QmsDefect) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteQmsDefect(id: number) {
  return request.delete(`${BASE}/${id}`)
}
