import request from './request'
import { idempotencyKey } from '@/utils/idempotencyKey'
import type { PageResult } from '@/types/api'

const BASE = '/admin/ai'

export interface AiQueryRequest {
  question: string
  days?: number
  sessionId?: string
  retrievalMode?: AiRetrievalMode
}

export type AiRetrievalMode = 'RAG' | 'GRAPHRAG' | 'FUSED'
export interface KnowledgeGraphNode {
  id: string
  name: string
  type: string
  source?: string
}
export interface KnowledgeGraphEdge {
  source: string
  target: string
  type: string
  documentSource?: string
}

export interface AiQueryResult {
  requestId?: string
  available: boolean
  answer?: string
  model?: string
  sources?: string[]
  citations?: string[]
  dataScope?: string
  dataTimeRange?: string
  fallback?: boolean
  message?: string
  graphNodes?: KnowledgeGraphNode[]
  graphEdges?: KnowledgeGraphEdge[]
}

export interface AiStreamStatus {
  requestId: string
  sessionId: string
  status: 'ACCEPTED' | 'STREAMING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  content: string
  sequence: number
  dataScope?: string
  dataTimeRange?: string
  model?: string
  fallback?: boolean
  sources?: string[]
  message?: string
  graphNodes?: KnowledgeGraphNode[]
  graphEdges?: KnowledgeGraphEdge[]
}

export interface AiKnowledgeFile {
  documentId: number
  name: string
  scope: 'PUBLIC' | 'PRIVATE'
  mimeType?: string
  size?: number
  chunkCount?: number
  indexStatus: string
  indexedAt?: string
}

export interface AiPlanSuggestion {
  planId: string
  status: string
  summary?: string
  affectedOrders?: string[]
  priorityOrder?: string[]
  constraints?: string[]
  resourceConstraints?: string[]
  materialConstraints?: string[]
  riskLevel: string
  riskBasis?: string
  dataTimeRange?: string
  sources?: string[]
  inputSummary?: string
  model?: string
  requestId?: string
  sessionId?: string
  createdAt?: string
}

export interface AiKnowledgeTask {
  taskId: string
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  scannedFiles: number
  filesChanged: number
  filesSkipped: number
  filesDeleted: number
  chunksIndexed: number
  chunksTotal: number
  failures: string[]
  message?: string
  createdAt?: string
  startedAt?: string
  finishedAt?: string
}

export interface AiGraphTypeCount {
  type: string
  count: number
}

export interface AiGraphOverview {
  nodeCount: number
  edgeCount: number
  nodeTypes: AiGraphTypeCount[]
  relationTypes: AiGraphTypeCount[]
  publicCount: number
  privateCount: number
}

export interface AiGraphNode {
  id: string
  name: string
  type: string
  scope: string
  ownerId?: number | string
  relationCount: number
}

export interface AiGraphEdge {
  fromId: string
  fromName: string
  fromType: string
  toId: string
  toName: string
  toType: string
  type: string
  source?: string
  scope: string
  ownerId?: number | string
}

export interface AiGraphNodeQuery {
  type?: string
  keyword?: string
  page?: number
  size?: number
}

export interface AiGraphEdgeQuery {
  nodeId?: string
  type?: string
  page?: number
  size?: number
}

export interface AiGraphEdgeKey {
  fromId: string
  toId: string
  type: string
}

export interface AiKnowledgeIndexResult {
  vectorStoreEnabled: boolean
  vectorStoreStatus?: 'ENABLED' | 'NOT_CONFIGURED' | 'UNAVAILABLE'
  knowledgePath: string
  scannedFiles: number
  indexedFiles?: number
  failedFiles?: number
  filesChanged: number
  filesSkipped: number
  filesDeleted: number
  chunksIndexed: number
  chunksTotal: number
  lastTaskId?: string
  lastTaskStatus?: AiKnowledgeTask['status']
  failures: string[]
  indexedAt?: string
  message?: string
}

export function queryAi(data: AiQueryRequest) {
  return request.post<AiQueryResult>(`${BASE}/query`, data)
}

export function getAiKnowledgeStatus() {
  return request.get<AiKnowledgeIndexResult>(`${BASE}/knowledge/status`)
}

export function indexAiKnowledge(maxChars = 1200, overlapChars = 160) {
  return request.post<AiKnowledgeTask>(`${BASE}/knowledge/index`, null, {
    params: { maxChars, overlapChars }
  })
}

export function getAiKnowledgeTask(taskId: string) {
  return request.get<AiKnowledgeTask>(`${BASE}/knowledge/tasks/${encodeURIComponent(taskId)}`)
}

export function listAiKnowledgeFiles(scope?: 'PUBLIC' | 'PRIVATE') {
  return request.get<AiKnowledgeFile[]>(`${BASE}/knowledge/files`, { params: scope ? { scope } : undefined })
}

export function uploadAiKnowledgeFile(file: File, scope: 'PUBLIC' | 'PRIVATE') {
  const body = new FormData()
  body.append('file', file)
  return request.post<AiKnowledgeFile>(`${BASE}/knowledge/files`, body, {
    params: { scope },
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function replaceAiKnowledgeFile(documentId: number, file: File) {
  const body = new FormData()
  body.append('file', file)
  return request.put<AiKnowledgeFile>(`${BASE}/knowledge/files/${documentId}`, body, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function deleteAiKnowledgeFile(documentId: number) {
  return request.delete<void>(`${BASE}/knowledge/files/${documentId}`)
}

export function downloadAiKnowledgeFile(documentId: number) {
  return request.exportBlob(`${BASE}/knowledge/files/${documentId}/download`)
}

export function startAiStream(data: AiQueryRequest) {
  const action = `ai-stream:${data.question}:${data.days ?? 7}:${data.retrievalMode ?? 'RAG'}`
  const key = idempotencyKey.getOrCreate(action)
  return request.post<{ requestId: string }>(`${BASE}/stream`, data, {
    headers: { 'Idempotency-Key': key }
  }).then(result => {
    idempotencyKey.clear(action)
    return result
  })
}

export function getAiStreamStatus(requestId: string) {
  return request.get<AiStreamStatus>(`${BASE}/stream/${encodeURIComponent(requestId)}`)
}

export function cancelAiStream(requestId: string) {
  return request.delete<void>(`${BASE}/stream/${encodeURIComponent(requestId)}`)
}

export function createPlan(data: AiQueryRequest) {
  return request.post<AiPlanSuggestion>(`${BASE}/plans`, data)
}

export function confirmPlan(planId: string) {
  const action = `ai-plan-confirm:${planId}`
  return request.post<AiPlanSuggestion>(`${BASE}/plans/${encodeURIComponent(planId)}/confirm`, null, {
    headers: { 'Idempotency-Key': idempotencyKey.getOrCreate(action) }
  }).then(result => {
    idempotencyKey.clear(action)
    return result
  })
}

/** AI 质检分析：质检不合格原因与建议（未配置 Key 时返回降级提示） */
export function analyzeDefect(inspectionId: number) {
  return request.post(`${BASE}/defect-analysis`, { inspectionId })
}

// ==================== 知识图谱管理 ====================

export function getAiGraphOverview() {
  return request.get<AiGraphOverview>(`${BASE}/graph/overview`)
}

export function listAiGraphNodes(params: AiGraphNodeQuery) {
  return request.get<PageResult<AiGraphNode>>(`${BASE}/graph/nodes`, { params })
}

export function listAiGraphEdges(params: AiGraphEdgeQuery) {
  return request.get<PageResult<AiGraphEdge>>(`${BASE}/graph/edges`, { params })
}

export function deleteAiGraphNode(nodeId: string) {
  return request.delete<void>(`${BASE}/graph/nodes/${encodeURIComponent(nodeId)}`)
}

export function deleteAiGraphEdge(params: AiGraphEdgeKey) {
  return request.delete<void>(`${BASE}/graph/edges`, { params })
}
