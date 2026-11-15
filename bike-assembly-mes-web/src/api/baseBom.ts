import request from './request'
import type { BaseBom, PageResult } from '@/types/api'

const BASE = '/admin/base-bom'

export function getBaseBomList(params?: Record<string, unknown>) {
  return request.get<PageResult<BaseBom>>(`${BASE}/list`, { params })
}

export function getBaseBomById(id: number) {
  return request.get(`${BASE}/${id}`)
}

export function createBaseBom(data: BaseBom) {
  return request.post(BASE, data)
}

export function updateBaseBom(id: number, data: BaseBom) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteBaseBom(id: number) {
  return request.delete(`${BASE}/${id}`)
}
