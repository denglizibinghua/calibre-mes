import request from './request'

/** 生产运营看板聚合数据 */
export function getDashboardOverview() {
  return request.get('/admin/dashboard/overview')
}

export interface ScreenSummary {
  activeWorkOrders: number
  todayCompletedWorkOrders: number
  overdueWorkOrders: number
  pendingInspections: number
  openAlerts: number
}
export interface ScreenProcess {
  processId: number
  processName: string
  total: number
  running: number
  waiting: number
  abnormal: number
  completed: number
  queued: number
}
export interface ScreenWorkOrder {
  workOrderId: number
  workOrderNo: string
  itemName: string | null
  unitName: string | null
  planQty: number
  unit: string | null
  status: string
  planEndTime: string | null
  totalSteps: number
  completedSteps: number
  overdue: boolean
}
export interface ScreenAlert {
  alertId: number
  alertNo: string
  alertType: string
  alertLevel: string
  status: string
  unitName: string | null
  equipmentName: string | null
  createTime: string
}
export interface DashboardScreen {
  generatedAt: string
  startDate: string
  endDate: string
  dataScope: 'ALL' | 'SELF'
  summary: ScreenSummary
  equipment: { available: number; fault: number; disabled: number; availabilityRate: number | null }
  quality: { inspected: number; passed: number; firstPassRate: number | null }
  trend: Array<{ date: string; planned: number; completed: number; inspected: number; passed: number }>
  processes: ScreenProcess[]
  workOrders: ScreenWorkOrder[]
  alerts: ScreenAlert[]
  defects: Array<{ name: string; value: number }>
  workUnits: Array<{ unitId: number; unitName: string; unitType: string }>
}

export function getDashboardScreen(params: { days: number; workUnitId?: number }) {
  return request.get<DashboardScreen>('/admin/dashboard/screen', { params })
}
