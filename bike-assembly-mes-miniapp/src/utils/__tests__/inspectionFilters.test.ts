import { describe, expect, it } from 'vitest'
import { filterInspections, getInspectionWorkOrders, type InspectionFilters } from '../inspectionFilters'
import type { QmsInspectionVO } from '@/types/inspection'

const list = [
  { inspectionId: 1, workOrderId: 10, workOrderNo: 'WO-10', status: '0', result: '0' },
  { inspectionId: 2, workOrderId: 10, workOrderNo: 'WO-10', status: '1', result: 'P' },
  { inspectionId: 3, workOrderId: 20, workOrderNo: 'WO-20', status: '1', result: 'F' },
  { inspectionId: 4, workOrderId: 20, workOrderNo: 'WO-20', status: '2', result: 'P' },
  { inspectionId: 5, workOrderId: 30, status: '2', result: 'F' },
] as QmsInspectionVO[]
const defaults: InspectionFilters = { workOrderId: 0, stage: '', confirmation: '', result: '' }
const ids = (filters: Partial<InspectionFilters>) => filterInspections(list, { ...defaults, ...filters }).map((item) => item.inspectionId)

describe('质检任务筛选', () => {
  it('默认展示全部，工单筛选可恢复到全部', () => {
    expect(ids({ workOrderId: 10 })).toEqual([1, 2])
    expect(ids({ workOrderId: 0 })).toEqual([1, 2, 3, 4, 5])
  })
  it('待质检仅包含待录入；已质检包含已录入和已确认', () => {
    expect(ids({ stage: 'pending' })).toEqual([1])
    expect(ids({ stage: 'inspected' })).toEqual([2, 3, 4, 5])
  })
  it('未确认包含待检及已录入，已确认只包含确认记录', () => {
    expect(ids({ confirmation: 'unconfirmed' })).toEqual([1, 2, 3])
    expect(ids({ confirmation: 'confirmed' })).toEqual([4, 5])
  })
  it('合格及不合格不会包含未录入结果', () => {
    expect(ids({ result: 'P' })).toEqual([2, 4])
    expect(ids({ result: 'F' })).toEqual([3, 5])
  })
  it('四组条件取交集', () => {
    expect(ids({ workOrderId: 20, stage: 'inspected', confirmation: 'unconfirmed', result: 'F' })).toEqual([3])
    expect(ids({ stage: 'pending', confirmation: 'confirmed' })).toEqual([])
  })
  it('无工单权限也能从可见任务生成编号、计数及去重选项', () => {
    expect(getInspectionWorkOrders(list)).toEqual([
      { value: 30, label: '工单 #30', count: 1 },
      { value: 10, label: 'WO-10', count: 2 },
      { value: 20, label: 'WO-20', count: 2 },
    ].sort((a, b) => a.label.localeCompare(b.label)))
    expect(getInspectionWorkOrders([])).toEqual([])
  })
})
