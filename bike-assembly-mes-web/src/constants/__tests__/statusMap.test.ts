import { describe, it, expect } from 'vitest'
import {
  WORK_ORDER_STATUS_MAP,
  WORK_PROCESS_STATUS_MAP,
  QC_RESULT_MAP,
  getStatusLabel,
  getStatusTagType,
} from '../statusMap'

describe('statusMap - Work Order', () => {
  it('should have all 7 work order statuses', () => {
    expect(Object.keys(WORK_ORDER_STATUS_MAP)).toHaveLength(7)
  })

  it('should return correct label for each status', () => {
    expect(getStatusLabel(WORK_ORDER_STATUS_MAP, '0')).toBe('已创建')
    expect(getStatusLabel(WORK_ORDER_STATUS_MAP, '1')).toBe('生产中')
    expect(getStatusLabel(WORK_ORDER_STATUS_MAP, '2')).toBe('已完工')
    expect(getStatusLabel(WORK_ORDER_STATUS_MAP, '3')).toBe('已关闭')
    expect(getStatusLabel(WORK_ORDER_STATUS_MAP, '4')).toBe('已下达')
    expect(getStatusLabel(WORK_ORDER_STATUS_MAP, '5')).toBe('已暂停')
    expect(getStatusLabel(WORK_ORDER_STATUS_MAP, '6')).toBe('已取消')
  })

  it('should return correct tag type for each status', () => {
    expect(getStatusTagType(WORK_ORDER_STATUS_MAP, '0')).toBe('info')
    expect(getStatusTagType(WORK_ORDER_STATUS_MAP, '2')).toBe('success')
    expect(getStatusTagType(WORK_ORDER_STATUS_MAP, '4')).toBe('warning')
    expect(getStatusTagType(WORK_ORDER_STATUS_MAP, '5')).toBe('warning')
    expect(getStatusTagType(WORK_ORDER_STATUS_MAP, '6')).toBe('danger')
  })

  it('should return "未知" for unknown status', () => {
    expect(getStatusLabel(WORK_ORDER_STATUS_MAP, 'X')).toBe('未知')
  })

  it('should return "info" for unknown tag type', () => {
    expect(getStatusTagType(WORK_ORDER_STATUS_MAP, 'X')).toBe('info')
  })
})

describe('statusMap - Work Process', () => {
  it('should have all 10 work process statuses', () => {
    expect(Object.keys(WORK_PROCESS_STATUS_MAP)).toHaveLength(10)
  })

  it('should return correct label for each status', () => {
    expect(getStatusLabel(WORK_PROCESS_STATUS_MAP, '0')).toBe('待开始')
    expect(getStatusLabel(WORK_PROCESS_STATUS_MAP, '1')).toBe('进行中')
    expect(getStatusLabel(WORK_PROCESS_STATUS_MAP, '2')).toBe('已完成')
    expect(getStatusLabel(WORK_PROCESS_STATUS_MAP, '3')).toBe('待检')
    expect(getStatusLabel(WORK_PROCESS_STATUS_MAP, '4')).toBe('检验合格')
    expect(getStatusLabel(WORK_PROCESS_STATUS_MAP, '5')).toBe('检验不合格')
    expect(getStatusLabel(WORK_PROCESS_STATUS_MAP, '6')).toBe('返工中')
    expect(getStatusLabel(WORK_PROCESS_STATUS_MAP, '7')).toBe('已跳过')
    expect(getStatusLabel(WORK_PROCESS_STATUS_MAP, '8')).toBe('已取消')
    expect(getStatusLabel(WORK_PROCESS_STATUS_MAP, '9')).toBe('可开工')
  })

  it('should have status 3 and 6 as warning', () => {
    expect(getStatusTagType(WORK_PROCESS_STATUS_MAP, '3')).toBe('warning')
    expect(getStatusTagType(WORK_PROCESS_STATUS_MAP, '6')).toBe('warning')
  })

  it('should have status 4 and 2 as success', () => {
    expect(getStatusTagType(WORK_PROCESS_STATUS_MAP, '4')).toBe('success')
    expect(getStatusTagType(WORK_PROCESS_STATUS_MAP, '2')).toBe('success')
  })
})

describe('statusMap - QC Result', () => {
  it('should have all 4 QC results', () => {
    expect(Object.keys(QC_RESULT_MAP)).toHaveLength(4)
  })

  it('should return correct labels', () => {
    expect(getStatusLabel(QC_RESULT_MAP, '0')).toBe('待检')
    expect(getStatusLabel(QC_RESULT_MAP, '1')).toBe('合格')
    expect(getStatusLabel(QC_RESULT_MAP, '2')).toBe('不合格')
    expect(getStatusLabel(QC_RESULT_MAP, '3')).toBe('待复检')
  })
})
