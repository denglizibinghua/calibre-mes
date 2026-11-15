import type { KnowledgeGraphEdge, KnowledgeGraphNode } from '@/api/ai'

export type {
  AiGraphEdge,
  AiGraphNode,
  AiGraphOverview,
  AiGraphTypeCount
} from '@/api/ai'


export interface AiCitation {
  index: number
  fileName: string
  title?: string
  chunk?: number
  updatedAt?: string
  raw: string
}

export type ChatStatus = 'pending' | 'streaming' | 'done' | 'error'

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  question?: string
  status: ChatStatus
  error?: string
  sources: string[]
  citations: AiCitation[]
  graphNodes: KnowledgeGraphNode[]
  graphEdges: KnowledgeGraphEdge[]
  model?: string
  fallback?: boolean
  dataTimeRange?: string
  dataScope?: string
  createdAt: number
  elapsedMs?: number
}
