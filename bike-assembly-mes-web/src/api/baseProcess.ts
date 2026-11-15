import request from './request'
import type { BaseProcess, PageResult } from '@/types/api'

const BASE = '/admin/base-process'

export function getBaseProcessList(params?: Record<string, unknown>) {
  return request.get<PageResult<BaseProcess>>(`${BASE}/list`, { params })
}

export function getBaseProcessById(id: number) {
  return request.get(`${BASE}/${id}`)
}

export function createBaseProcess(data: BaseProcess) {
  return request.post(BASE, data)
}

export function updateBaseProcess(id: number, data: BaseProcess) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteBaseProcess(id: number) {
  return request.delete(`${BASE}/${id}`)
}
