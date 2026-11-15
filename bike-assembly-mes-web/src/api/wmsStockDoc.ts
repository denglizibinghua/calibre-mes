import request from './request'
import type { WmsStockDoc, PageResult } from '@/types/api'

const BASE = '/admin/wms-stock-doc'

export function getWmsStockDocList(params?: Record<string, unknown>) {
  return request.get<PageResult<WmsStockDoc>>(`${BASE}/list`, { params })
}

export function getWmsStockDocById(id: number) {
  return request.get(`${BASE}/${id}`)
}

export function createWmsStockDoc(data: WmsStockDoc) {
  return request.post(BASE, data)
}

export function updateWmsStockDoc(id: number, data: WmsStockDoc) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteWmsStockDoc(id: number) {
  return request.delete(`${BASE}/${id}`)
}

/** 执行库存单据：库存增减 + 流水，单据状态变为已执行 */
export function executeStockDoc(id: number) {
  return request.put(`${BASE}/${id}/execute`)
}
