/**
 * V3.4 工序流转状态机 — 统一状态映射。
 * 管理端和小程序共用此文作为唯一状态定义来源。
 */

// ==================== 工单状态 ====================

export interface StatusEntry {
  label: string
  tagType: 'primary' | 'success' | 'warning' | 'danger' | 'info'
}

export const WORK_ORDER_STATUS_MAP: Record<string, StatusEntry> = {
  '0': { label: '已创建',   tagType: 'info' },
  '1': { label: '生产中',   tagType: 'primary' },
  '2': { label: '已完工',   tagType: 'success' },
  '3': { label: '已关闭',   tagType: 'info' },
  '4': { label: '已下达',   tagType: 'warning' },
  '5': { label: '已暂停',   tagType: 'warning' },
  '6': { label: '已取消',   tagType: 'danger' },
}

// ==================== 工序状态 ====================

export const WORK_PROCESS_STATUS_MAP: Record<string, StatusEntry> = {
  '0': { label: '待开始',     tagType: 'info' },
  '1': { label: '进行中',     tagType: 'primary' },
  '2': { label: '已完成',     tagType: 'success' },
  '3': { label: '待检',       tagType: 'warning' },
  '4': { label: '检验合格',   tagType: 'success' },
  '5': { label: '检验不合格', tagType: 'danger' },
  '6': { label: '返工中',     tagType: 'warning' },
  '7': { label: '已跳过',     tagType: 'info' },
  '8': { label: '已取消',     tagType: 'danger' },
  '9': { label: '可开工',     tagType: 'primary' },
}

// ==================== 质检结果 ====================

export const QC_RESULT_MAP: Record<string, StatusEntry> = {
  '0': { label: '待检',   tagType: 'warning' },
  '1': { label: '合格',   tagType: 'success' },
  '2': { label: '不合格', tagType: 'danger' },
  '3': { label: '待复检', tagType: 'warning' },
}

// ==================== 工具函数 ====================

export function getStatusLabel(
  map: Record<string, StatusEntry>,
  code: string,
): string {
  return map[code]?.label ?? '未知'
}

export function getStatusTagType(
  map: Record<string, StatusEntry>,
  code: string,
): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  return map[code]?.tagType ?? 'info'
}
