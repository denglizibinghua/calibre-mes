export type ScanAction = 'WORK_ORDER_DETAIL' | 'WORK_PROCESS' | 'INSPECTION_PENDING'

export interface ScanResolveDTO {
  content: string
}

export interface ScanResolveVO {
  businessType: 'WORK_ORDER'
  workOrderId: number
  workOrderNo: string
  actions: ScanAction[]
  pendingInspectionCount: number
}
