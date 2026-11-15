import request from './request'
import type { PageResult } from '@/types/api'

const BASE = '/admin/report'

export interface WorkOrderReportRow {
  workOrderId?: number; workOrderNo?: string; orderNo?: string;
  itemId?: number; itemCode?: string; itemName?: string;
  planQty?: number; unit?: string; status?: string;
  planStartTime?: string; planEndTime?: string; createTime?: string;
}

export interface InventoryReportRow {
  inventoryId?: number; itemId?: number; itemCode?: string; itemName?: string;
  locationId?: number; locationCode?: string; locationName?: string;
  quantity?: number; lockedQuantity?: number; unit?: string;
  batchNo?: string; updateTime?: string;
}

export interface QualityReportRow {
  inspectionId?: number; inspectionNo?: string; workOrderId?: number;
  workOrderNo?: string; itemName?: string; itemCode?: string;
  result?: string; inspector?: string; inspectionTime?: string; status?: string;
}

export interface EquipmentRepairReportRow {
  repairId?: number; equipmentId?: number; equipmentCode?: string; equipmentName?: string;
  faultDesc?: string; reportUser?: string; repairUser?: string;
  status?: string; reportTime?: string; finishTime?: string; resultDesc?: string;
}

export interface AndonReportRow {
  alertId?: number; alertNo?: string; alertType?: string; alertLevel?: string;
  unitId?: number; unitName?: string; equipmentId?: number;
  status?: string; createTime?: string; handleTime?: string;
}

export function getWorkOrderReport(params?: Record<string, unknown>) {
  return request.get<PageResult<WorkOrderReportRow>>(`${BASE}/work-order`, { params })
}
export function exportWorkOrderReport(params?: Record<string, unknown>) {
  return request.exportBlob(`${BASE}/work-order/export`, params)
}

export function getInventoryReport(params?: Record<string, unknown>) {
  return request.get<PageResult<InventoryReportRow>>(`${BASE}/inventory`, { params })
}
export function exportInventoryReport(params?: Record<string, unknown>) {
  return request.exportBlob(`${BASE}/inventory/export`, params)
}

export function getQualityReport(params?: Record<string, unknown>) {
  return request.get<PageResult<QualityReportRow>>(`${BASE}/quality`, { params })
}
export function exportQualityReport(params?: Record<string, unknown>) {
  return request.exportBlob(`${BASE}/quality/export`, params)
}

export function getEquipmentRepairReport(params?: Record<string, unknown>) {
  return request.get<PageResult<EquipmentRepairReportRow>>(`${BASE}/equipment-repair`, { params })
}
export function exportEquipmentRepairReport(params?: Record<string, unknown>) {
  return request.exportBlob(`${BASE}/equipment-repair/export`, params)
}

export function getAndonReport(params?: Record<string, unknown>) {
  return request.get<PageResult<AndonReportRow>>(`${BASE}/andon`, { params })
}
export function exportAndonReport(params?: Record<string, unknown>) {
  return request.exportBlob(`${BASE}/andon/export`, params)
}
