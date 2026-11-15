import request from './request'
import type { PageResult, QmsInspection } from '@/types/api'

const BASE = '/admin/qms-inspection'

/**
 * 质检单只读接口（录入/确认仅在小程序端 /app/** 执行）。
 */
export function getQmsInspectionList(params?: Record<string, unknown>) {
  return request.get<PageResult<QmsInspection>>(`${BASE}/list`, { params })
}

export function getQmsInspectionById(id: number) {
  return request.get(`${BASE}/${id}`)
}
