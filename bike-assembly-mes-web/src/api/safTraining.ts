import request from './request'
import type { SafTraining, PageResult } from '@/types/api'

const BASE = '/admin/saf-training'

export function getSafTrainingList(params?: Record<string, unknown>) {
  return request.get<PageResult<SafTraining>>(`${BASE}/list`, { params })  // V2.3 分页
}

export function getSafTrainingById(id: number) {
  return request.get(`${BASE}/${id}`)
}

export function createSafTraining(data: SafTraining) {
  return request.post(BASE, data)
}

export function updateSafTraining(id: number, data: SafTraining) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteSafTraining(id: number) {
  return request.delete(`${BASE}/${id}`)
}
