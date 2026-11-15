import { describe, it, expect } from 'vitest'
import {
  WORK_ORDER_STATUS_MAP,
  getWorkOrderStatusLabel,
  getWorkOrderStatusClass,
} from '../workOrder'

describe('workOrder status mappings', () => {
  it('should have all 7 work order statuses', () => {
    expect(Object.keys(WORK_ORDER_STATUS_MAP)).toHaveLength(7)
  })

  it('should return correct label for each status', () => {
    expect(getWorkOrderStatusLabel('0')).toBe('已创建')
    expect(getWorkOrderStatusLabel('1')).toBe('生产中')
    expect(getWorkOrderStatusLabel('2')).toBe('已完工')
    expect(getWorkOrderStatusLabel('3')).toBe('已关闭')
    expect(getWorkOrderStatusLabel('4')).toBe('已下达')
    expect(getWorkOrderStatusLabel('5')).toBe('已暂停')
    expect(getWorkOrderStatusLabel('6')).toBe('已取消')
  })

  it('should return raw code for unknown status', () => {
    expect(getWorkOrderStatusLabel('X')).toBe('X')
  })

  it('should return correct CSS class for each status', () => {
    expect(getWorkOrderStatusClass('0')).toBe('pending')
    expect(getWorkOrderStatusClass('1')).toBe('in-progress')
    expect(getWorkOrderStatusClass('2')).toBe('completed')
    expect(getWorkOrderStatusClass('3')).toBe('closed')
    expect(getWorkOrderStatusClass('4')).toBe('released')
    expect(getWorkOrderStatusClass('5')).toBe('paused')
    expect(getWorkOrderStatusClass('6')).toBe('cancelled')
  })

  it('should return "default" for unknown status class', () => {
    expect(getWorkOrderStatusClass('X')).toBe('default')
  })
})
