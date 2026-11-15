import type { AiCitation } from '@/views/ai/types'

type TagType = 'success' | 'warning' | 'danger' | 'info'

interface StatusMeta {
  label: string
  type: TagType
}

const STATUS_META: Record<string, StatusMeta> = {
  INDEXED: { label: '已索引', type: 'success' },
  PENDING: { label: '待索引', type: 'warning' },
  INDEXING: { label: '索引中', type: 'warning' },
  FAILED: { label: '失败', type: 'danger' },
  QUEUED: { label: '排队中', type: 'info' },
  RUNNING: { label: '执行中', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' }
}

const CITATION_PREFIX = /^\[来源\s*(\d+)?\]\s*/

/**
 * 解析后端返回的 sources 中的引用条目，例如：
 * [来源1] 03-前端Web管理端架构.md（自行车组装 MES 系统 — 前端 Web 管理端架构），chunk 0，更新时间 2026-11-07T15:45:35.679467500
 * 非引用条目会被忽略；以 [来源 开头但结构损坏的条目仍会生成卡片（fileName = 原文）。
 */
export function parseCitations(sources?: string[]): AiCitation[] {
  if (!sources?.length) return []
  const citations: AiCitation[] = []

  sources.forEach((source, position) => {
    const raw = source.trim()
    if (!raw.startsWith('[来源')) return

    const prefix = raw.match(CITATION_PREFIX)
    const parsedIndex = prefix?.[1] ? Number(prefix[1]) : Number.NaN
    const index = Number.isFinite(parsedIndex) && parsedIndex > 0 ? parsedIndex : position + 1
    const body = prefix ? raw.slice(prefix[0].length).trim() : ''
    const citation: AiCitation = {
      index,
      fileName: body || raw,
      raw
    }

    if (body) {
      const titleMatch = body.match(/（([^（）]*)）/)
      if (titleMatch?.[1] && titleMatch.index != null) {
        citation.title = titleMatch[1].trim()
        citation.fileName = body.slice(0, titleMatch.index).trim() || citation.fileName
      }

      const chunkMatch = body.match(/chunk\s*(\d+)/i)
      if (chunkMatch?.[1]) {
        citation.chunk = Number(chunkMatch[1])
        citation.fileName = citation.fileName.replace(/[，,]\s*chunk\s*\d+/gi, '').trim()
      }

      const updatedMatch = body.match(
        /更新时间\s*(\d{4}-\d{2}-\d{2}(?:[T\s]\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?)?|[^\s，,）)]+)/
      )
      if (updatedMatch?.[1]) {
        citation.updatedAt = updatedMatch[1].trim()
        citation.fileName = citation.fileName.replace(/[，,]\s*更新时间\s*.*$/i, '').trim()
      }
    }

    citation.fileName = citation.fileName.replace(/^[，,\s]+|[，,\s]+$/g, '') || raw
    citations.push(citation)
  })

  return citations
}

/** 将 sources 拆分为 MES 数据依据与知识库引用依据。 */
export function splitSources(sources?: string[]): { dataSources: string[]; citationSources: string[] } {
  const dataSources: string[] = []
  const citationSources: string[] = []

  ;(sources ?? []).forEach(source => {
    const value = source.trim()
    if (!value) return
    if (value.startsWith('[来源')) citationSources.push(value)
    else dataSources.push(value)
  })

  return { dataSources, citationSources }
}

/** 2026-11-07T15:45:35.679467500 → 2026-11-07 15:45；无效值原样返回。 */
export function formatDateTime(value?: string): string {
  if (!value) return '-'
  const trimmed = value.trim()
  if (!trimmed) return '-'

  const match = trimmed.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}):(\d{2})/)
  if (!match) return value
  return `${match[1]} ${match[2]}:${match[3]}`
}

/** 文件大小：B / KB / MB；空值返回 -。 */
export function formatFileSize(size?: number | null): string {
  if (size == null || !Number.isFinite(size) || size < 0) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

/** 文件索引状态中文标签与 el-tag 类型；未知状态原样展示。 */
export function indexStatusMeta(status?: string): StatusMeta {
  if (!status) return { label: '-', type: 'info' }
  return STATUS_META[status] ?? { label: status, type: 'info' }
}

/** 索引任务状态中文标签与 el-tag 类型；未知状态原样展示。 */
export function taskStatusMeta(status?: string): StatusMeta {
  if (!status) return { label: '-', type: 'info' }
  return STATUS_META[status] ?? { label: status, type: 'info' }
}
