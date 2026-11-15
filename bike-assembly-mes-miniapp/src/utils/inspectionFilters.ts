import type { QmsInspectionVO } from '@/types/inspection'

export interface InspectionFilters {
  workOrderId: number
  stage: '' | 'pending' | 'inspected'
  confirmation: '' | 'unconfirmed' | 'confirmed'
  result: '' | 'P' | 'F'
}

export function filterInspections(list: QmsInspectionVO[], filters: InspectionFilters): QmsInspectionVO[] {
  return list.filter((item) => {
    const status = String(item.status)
    return (!filters.workOrderId || Number(item.workOrderId) === filters.workOrderId)
      && (!filters.stage || (filters.stage === 'pending' ? status === '0' : status === '1' || status === '2'))
      && (!filters.confirmation || (filters.confirmation === 'confirmed' ? status === '2' : status === '0' || status === '1'))
      && (!filters.result || item.result === filters.result)
  })
}

/** 仅从已获授权的质检任务提取工单，不依赖生产工单权限。 */
export function getInspectionWorkOrders(list: QmsInspectionVO[]): Array<{ value: number; label: string; count: number }> {
  const options = new Map<number, { value: number; label: string; count: number }>()
  list.forEach((item) => {
    const id = Number(item.workOrderId)
    if (!Number.isFinite(id) || id <= 0) return
    const existing = options.get(id)
    if (existing) existing.count++
    else options.set(id, { value: id, label: item.workOrderNo || `工单 #${id}`, count: 1 })
  })
  return [...options.values()].sort((a, b) => a.label.localeCompare(b.label))
}
