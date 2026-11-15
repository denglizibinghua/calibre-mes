<template>
  <PageContainer
    title="AI 助手"
    description="基于权限内的 MES 数据与知识库，生成可追溯的分析结论与排产建议。"
  >
    <template #header>
      <div class="header-meta">
        <span class="conn-pill" :class="`is-${wsState}`">
          <span class="conn-dot" aria-hidden="true" />
          {{ wsStateLabel }}
        </span>
        <span class="session-chip" :title="sessionId">会话 {{ sessionShortId }}</span>
        <el-button size="small" @click="graphManagerVisible = true">
          <el-icon><Connection /></el-icon>
          知识图谱管理
        </el-button>
        <el-button type="primary" plain size="small" @click="startNewSession">
          <el-icon><Plus /></el-icon>
          新会话
        </el-button>
      </div>
    </template>

    <div class="ai-workspace">
      <section class="chat-panel" aria-label="MES 智能问答">
        <header class="chat-header">
          <div class="chat-heading">
            <h2>MES 智能问答</h2>
            <p>AI 只读当前权限范围内的数据，不会直接修改业务数据。</p>
          </div>
          <span v-if="streaming" class="streaming-badge">
            <span class="streaming-dot" aria-hidden="true" />
            生成中
          </span>
        </header>

        <div
          ref="chatLogRef"
          class="chat-log"
          :class="{ 'is-streaming': streaming }"
          aria-live="polite"
        >
          <div v-if="messages.length === 0" class="welcome-card">
            <div class="welcome-icon" aria-hidden="true">
              <el-icon :size="22"><MagicStick /></el-icon>
            </div>
            <h2>开始一次可追溯的 MES 问答</h2>
            <p class="welcome-subtitle">
              选择下面的示例问题，或直接输入。回答会引用当前权限内的 MES 数据与知识库文档，并附可点击的来源。
            </p>
            <div class="suggestion-grid">
              <button
                v-for="suggestion in suggestions"
                :key="suggestion"
                type="button"
                class="suggestion-chip"
                @click="sendSuggestion(suggestion)"
              >
                <el-icon class="suggestion-icon" aria-hidden="true"><ChatLineSquare /></el-icon>
                <span>{{ suggestion }}</span>
              </button>
            </div>
          </div>

          <ChatMessage
            v-for="message in messages"
            :key="message.id"
            :message="message"
            :active="message.id === activeAssistantId && message.status === 'streaming'"
            @retry="retryMessage"
            @copy="copyMessage"
            @open-graph="openGraph"
          />
        </div>

        <footer class="composer">
          <el-input
            v-model="question"
            type="textarea"
            :rows="4"
            maxlength="2000"
            show-word-limit
            resize="none"
            placeholder="输入问题，Enter 发送，Shift + Enter 换行"
            aria-label="输入问题"
            @keydown="handleComposerKeydown"
          />
          <div class="composer-controls">
            <div class="control-group">
              <span class="control-label">检索模式</span>
              <el-radio-group v-model="retrievalMode" size="small">
                <el-radio-button value="RAG">RAG</el-radio-button>
                <el-radio-button value="GRAPHRAG">GraphRAG</el-radio-button>
                <el-radio-button value="FUSED">融合</el-radio-button>
              </el-radio-group>
            </div>
            <div class="control-group">
              <span class="control-label">统计天数</span>
              <el-input-number
                v-model="days"
                class="days-input"
                size="small"
                :min="1"
                :max="30"
                controls-position="right"
              />
            </div>
            <div class="composer-actions">
              <el-button type="primary" :loading="streaming" :disabled="!canSend" @click="sendStream">
                <el-icon v-if="!streaming"><Promotion /></el-icon>
                发送
              </el-button>
              <el-button :loading="querying" :disabled="!canSend" @click="sendSync">
                <el-icon v-if="!querying"><ChatLineSquare /></el-icon>
                非流式
              </el-button>
              <el-button v-if="streaming" type="danger" plain @click="stopStream">
                <el-icon><VideoPause /></el-icon>
                停止生成
              </el-button>
            </div>
          </div>
          <div class="composer-hint">
            <span>统计口径：近 {{ days }} 天</span>
            <span>Enter 发送 · Shift + Enter 换行</span>
          </div>
        </footer>
      </section>

      <aside class="side-panel" aria-label="AI 辅助面板">
        <el-tabs v-model="activeTab" class="side-tabs">
          <el-tab-pane label="知识库" name="knowledge">
            <KnowledgePanel
              :status="knowledge"
              :task="knowledgeTask"
              :files="knowledgeFiles"
              :files-loading="filesLoading"
              :busy="fileBusy"
              :indexing="indexingKnowledge"
              :can-manage="canManagePublic"
              @refresh-status="refreshKnowledge"
              @refresh-files="refreshFiles"
              @index="indexKnowledge"
              @upload="uploadKnowledgeFile"
              @replace="replaceKnowledgeFile"
              @delete="removeKnowledgeFile"
              @download="downloadKnowledgeFile"
            />
          </el-tab-pane>
          <el-tab-pane label="排产建议" name="plan">
            <PlanPanel
              :plan="plan"
              :planning="planning"
              :confirming="confirming"
              :days="days"
              :can-confirm="true"
              @generate="createPlan"
              @confirm="confirmPlan"
            />
          </el-tab-pane>
        </el-tabs>
      </aside>
    </div>

    <GraphDialog
      v-model="graphVisible"
      :nodes="graphNodes"
      :edges="graphEdges"
      :title="graphTitle"
    />

    <GraphManager v-model="graphManagerVisible" />
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatLineSquare, Connection, MagicStick, Plus, Promotion, VideoPause } from '@element-plus/icons-vue'
import PageContainer from '@/components/PageContainer.vue'
import ChatMessage from '@/views/ai/components/ChatMessage.vue'
import KnowledgePanel from '@/views/ai/components/KnowledgePanel.vue'
import PlanPanel from '@/views/ai/components/PlanPanel.vue'
import GraphDialog from '@/views/ai/components/GraphDialog.vue'
import GraphManager from '@/views/ai/components/GraphManager.vue'
import { parseCitations } from '@/views/ai/citation'
import type { ChatMessage as ChatMessageModel } from '@/views/ai/types'
import {
  cancelAiStream,
  confirmPlan as _confirmPlan,
  createPlan as _createPlan,
  deleteAiKnowledgeFile,
  downloadAiKnowledgeFile,
  getAiKnowledgeStatus,
  getAiKnowledgeTask,
  getAiStreamStatus,
  indexAiKnowledge,
  listAiKnowledgeFiles,
  queryAi,
  replaceAiKnowledgeFile,
  startAiStream,
  uploadAiKnowledgeFile,
  type AiKnowledgeFile,
  type AiKnowledgeIndexResult,
  type AiKnowledgeTask,
  type AiPlanSuggestion,
  type AiRetrievalMode,
  type AiStreamStatus,
  type KnowledgeGraphEdge,
  type KnowledgeGraphNode
} from '@/api/ai'
import { useWebSocket, type AiWsMessage } from '@/utils/useWebSocket'
import { useUserStore } from '@/stores/user'

const REQUEST_KEY = 'ai-stream-request'
const SESSION_KEY = 'ai-stream-session'
const SUGGESTIONS = [
  '过去 7 天哪些工序的质量异常最多？',
  '结合设备状态和待处理工单，给出明日排产优先级。',
  '工单状态机有哪些状态流转？',
  '当前库存有哪些缺料风险？'
]

const userStore = useUserStore()
const ws = useWebSocket()

const sessionId = ref(sessionStorage.getItem(SESSION_KEY) || createSessionId())
const currentRequestId = ref(sessionStorage.getItem(REQUEST_KEY) || '')
const messages = ref<ChatMessageModel[]>([])
const activeAssistantId = ref<string | null>(null)
const lastSeq = ref(0)
const streaming = ref(false)
const querying = ref(false)
const question = ref('')
const days = ref(7)
const retrievalMode = ref<AiRetrievalMode>('RAG')
const activeTab = ref<'knowledge' | 'plan'>('knowledge')
const wsState = ref<'connecting' | 'online' | 'offline'>('connecting')
const chatLogRef = ref<HTMLElement | null>(null)

const knowledge = ref<AiKnowledgeIndexResult | null>(null)
const knowledgeTask = ref<AiKnowledgeTask | null>(null)
const knowledgeFiles = ref<AiKnowledgeFile[]>([])
const filesLoading = ref(false)
const fileBusy = ref(false)
const indexingKnowledge = ref(false)

const plan = ref<AiPlanSuggestion | null>(null)
const planning = ref(false)
const confirming = ref(false)

const graphVisible = ref(false)
const graphNodes = ref<KnowledgeGraphNode[]>([])
const graphEdges = ref<KnowledgeGraphEdge[]>([])
const graphTitle = ref('')
const graphManagerVisible = ref(false)

const suggestions = SUGGESTIONS

let stopAiListener: (() => void) | null = null
let stopConnectionListener: (() => void) | null = null
let streamPollTimer: number | null = null
let connectingTimer: number | null = null
let knowledgePollTimer: number | null = null
let knowledgePollActive = false
let knowledgePollAttempts = 0
let knowledgePollTaskId = ''
let fileRefreshTimers: number[] = []
let streamGeneration = 0
let recoveringStream = false
let disposed = false
let messageCounter = 0
const transientTimers = new Set<number>()

const wsStateLabel = computed(() => {
  if (wsState.value === 'online') return '实时连接'
  if (wsState.value === 'connecting') return '连接中'
  return '离线'
})

const sessionShortId = computed(() => {
  const value = sessionId.value.replace(/^ai-/, '')
  return value.length > 8 ? value.slice(-8) : value
})

const canSend = computed(() => (
  !streaming.value && !querying.value && question.value.trim().length > 0
))

const canManagePublic = computed(() => (
  userStore.permissions.includes('*:*:*')
  || userStore.permissions.includes('admin:ai:knowledge-manage')
))

onMounted(() => {
  sessionStorage.setItem(SESSION_KEY, sessionId.value)
  ws.connect()
  stopConnectionListener = ws.onWsConnectionChange(value => {
    if (value) {
      wsState.value = 'online'
      clearConnectingTimer()
      if (currentRequestId.value) void recoverStream()
    } else if (wsState.value === 'online') {
      wsState.value = 'offline'
    }
  })
  stopAiListener = ws.onAiMessage(handleAiMessage)
  connectingTimer = window.setTimeout(() => {
    connectingTimer = null
    if (wsState.value === 'connecting') wsState.value = 'offline'
  }, 3000)
  if (currentRequestId.value) void recoverStream()
  void refreshKnowledge()
  void refreshFiles()
})

onBeforeUnmount(() => {
  disposed = true
  stopAiListener?.()
  stopConnectionListener?.()
  stopAiListener = null
  stopConnectionListener = null
  stopStreamFallback()
  stopKnowledgePolling()
  clearConnectingTimer()
  clearFileRefreshTimers()
  transientTimers.forEach(timer => window.clearTimeout(timer))
  transientTimers.clear()
})

watch(messages, () => scrollToBottom(), { deep: true })

function createSessionId(): string {
  return `ai-${crypto.randomUUID?.() ?? Date.now()}`
}

function nextMessageId(role: 'user' | 'assistant'): string {
  messageCounter += 1
  return `${role}-${Date.now()}-${messageCounter}`
}

function createUserMessage(content: string): ChatMessageModel {
  return {
    id: nextMessageId('user'),
    role: 'user',
    content,
    status: 'done',
    sources: [],
    citations: [],
    graphNodes: [],
    graphEdges: [],
    createdAt: Date.now()
  }
}

function createAssistantMessage(questionText: string): ChatMessageModel {
  return {
    id: nextMessageId('assistant'),
    role: 'assistant',
    content: '',
    question: questionText,
    status: 'pending',
    sources: [],
    citations: [],
    graphNodes: [],
    graphEdges: [],
    createdAt: Date.now()
  }
}

function scrollToBottom() {
  void nextTick(() => {
    const element = chatLogRef.value
    if (!element) return
    element.scrollTop = element.scrollHeight
  })
}

function handleComposerKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return
  event.preventDefault()
  void sendStream()
}

function sendSuggestion(text: string) {
  question.value = text
  if (canSend.value) void sendStream()
}

async function sendStream() {
  if (streaming.value || querying.value) return
  const text = question.value.trim()
  if (!text) return
  messages.value.push(createUserMessage(text))
  const assistant = createAssistantMessage(text)
  messages.value.push(assistant)
  await beginStream(assistant, text)
}

async function beginStream(assistant: ChatMessageModel, text: string) {
  const generation = ++streamGeneration
  activeAssistantId.value = assistant.id
  streaming.value = true
  lastSeq.value = 0
  currentRequestId.value = ''
  sessionStorage.removeItem(REQUEST_KEY)
  stopStreamFallback()
  try {
    const result = await startAiStream({
      question: text,
      days: days.value,
      sessionId: sessionId.value,
      retrievalMode: retrievalMode.value
    })
    if (generation !== streamGeneration) {
      void cancelAiStream(result.requestId).catch(() => { /* 请求可能已结束 */ })
      return
    }
    currentRequestId.value = result.requestId
    sessionStorage.setItem(REQUEST_KEY, result.requestId)
    assistant.status = 'streaming'
    startStreamFallback()
  } catch (error) {
    if (generation !== streamGeneration) return
    assistant.status = 'error'
    assistant.error = streamErrorMessage(error)
    assistant.elapsedMs = Date.now() - assistant.createdAt
    finishStream()
  }
}

async function sendSync() {
  if (streaming.value || querying.value) return
  const text = question.value.trim()
  if (!text) return
  messages.value.push(createUserMessage(text))
  const assistant = createAssistantMessage(text)
  messages.value.push(assistant)
  querying.value = true
  try {
    const result = await queryAi({
      question: text,
      days: days.value,
      sessionId: sessionId.value,
      retrievalMode: retrievalMode.value
    })
    assistant.content = result.answer || result.message || ''
    assistant.sources = result.sources ?? []
    assistant.citations = parseCitations(
      result.citations?.length ? result.citations : result.sources
    )
    assistant.graphNodes = result.graphNodes ?? []
    assistant.graphEdges = result.graphEdges ?? []
    assistant.model = result.model
    assistant.fallback = Boolean(result.fallback)
    assistant.dataTimeRange = result.dataTimeRange
    assistant.dataScope = result.dataScope
    assistant.status = 'done'
    assistant.elapsedMs = Date.now() - assistant.createdAt
  } catch (error) {
    assistant.status = 'error'
    assistant.error = queryErrorMessage(error)
    assistant.elapsedMs = Date.now() - assistant.createdAt
  } finally {
    querying.value = false
  }
}

async function retryMessage(messageId: string) {
  if (streaming.value || querying.value) return
  const index = messages.value.findIndex(
    message => message.id === messageId && message.role === 'assistant'
  )
  if (index < 0) return
  const text = messages.value[index].question?.trim()
  if (!text) return
  messages.value.splice(index, 1)
  const assistant = createAssistantMessage(text)
  messages.value.push(assistant)
  await beginStream(assistant, text)
}

async function stopStream() {
  if (!streaming.value) return
  streamGeneration += 1
  const requestId = currentRequestId.value
  const assistant = findActiveAssistant()
  if (assistant) {
    assistant.status = 'error'
    assistant.error = assistant.content
      ? '已停止生成，以上为已生成的部分内容。'
      : '已停止生成。'
    assistant.elapsedMs = Date.now() - assistant.createdAt
  }
  finishStream()
  if (requestId) {
    try {
      await cancelAiStream(requestId)
    } catch { /* 请求可能已结束 */ }
  }
}

async function startNewSession() {
  if (streaming.value) {
    try {
      await ElMessageBox.confirm(
        '当前回答仍在生成，新建会话将停止接收当前回答，是否继续？',
        '新建会话',
        {
          type: 'warning',
          confirmButtonText: '新建会话',
          cancelButtonText: '继续等待'
        }
      )
    } catch { return }
  }
  const requestId = currentRequestId.value
  streamGeneration += 1
  stopStreamFallback()
  sessionId.value = createSessionId()
  sessionStorage.setItem(SESSION_KEY, sessionId.value)
  sessionStorage.removeItem(REQUEST_KEY)
  currentRequestId.value = ''
  activeAssistantId.value = null
  lastSeq.value = 0
  streaming.value = false
  messages.value = []
  question.value = ''
  graphVisible.value = false
  graphNodes.value = []
  graphEdges.value = []
  if (requestId) {
    void cancelAiStream(requestId).catch(() => { /* 请求可能已结束 */ })
  }
}

function handleAiMessage(message: AiWsMessage) {
  if (message.data.sessionId && message.data.sessionId !== sessionId.value) return
  const eventRequestId = readString(message.data.requestId)
  if (
    eventRequestId
    && currentRequestId.value
    && eventRequestId !== currentRequestId.value
  ) return

  if (message.eventType === 'AI_REQUEST_ACCEPTED') {
    if (eventRequestId) {
      currentRequestId.value = eventRequestId
      sessionStorage.setItem(REQUEST_KEY, eventRequestId)
      const assistant = findActiveAssistant()
      if (assistant && assistant.status === 'pending') assistant.status = 'streaming'
    }
    return
  }

  if (message.eventType === 'AI_DELTA') {
    if (!streaming.value) return
    const seq = Number(message.data.seq || 0)
    if (seq > 0 && seq <= lastSeq.value) return
    if (seq > 0) lastSeq.value = seq
    const assistant = ensureActiveAssistant()
    assistant.status = 'streaming'
    assistant.content += readString(message.data.delta) ?? ''
    applyStreamMeta(assistant, message.data)
    return
  }

  if (message.eventType === 'AI_COMPLETED') {
    if (!streaming.value) return
    const assistant = ensureActiveAssistant()
    applyStreamMeta(assistant, message.data)
    assistant.status = 'done'
    assistant.elapsedMs = Date.now() - assistant.createdAt
    finishStream()
    return
  }

  if (message.eventType === 'AI_ERROR') {
    if (!streaming.value) return
    const assistant = ensureActiveAssistant()
    assistant.status = 'error'
    assistant.error = readString(message.data.message) ?? 'AI 请求失败'
    assistant.elapsedMs = Date.now() - assistant.createdAt
    finishStream()
    return
  }

  if (message.eventType === 'AI_PLAN_READY') {
    ElMessage.success(`排产建议已生成：${readString(message.data.planId) ?? ''}`)
  }
}

function findActiveAssistant(): ChatMessageModel | undefined {
  if (!activeAssistantId.value) return undefined
  return messages.value.find(
    message => message.id === activeAssistantId.value && message.role === 'assistant'
  )
}

function ensureActiveAssistant(): ChatMessageModel {
  const current = findActiveAssistant()
  if (current) return current
  const assistant = createAssistantMessage('上次会话')
  messages.value.push(assistant)
  activeAssistantId.value = assistant.id
  return assistant
}

function applyStreamMeta(assistant: ChatMessageModel, data: Record<string, unknown>) {
  const sources = readSources(data.sources)
  if (sources) {
    assistant.sources = sources
    assistant.citations = parseCitations(sources)
  }
  const nodes = readNodes(data.graphNodes)
  if (nodes) assistant.graphNodes = nodes
  const edges = readEdges(data.graphEdges)
  if (edges) assistant.graphEdges = edges
  const model = readString(data.model)
  if (model) assistant.model = model
  const timeRange = readString(data.dataTimeRange)
  if (timeRange) assistant.dataTimeRange = timeRange
  const scope = readString(data.dataScope)
  if (scope) assistant.dataScope = scope
  const fallback = readBoolean(data.fallback)
  if (fallback !== null) assistant.fallback = fallback
}

function applyRecoveredStatus(assistant: ChatMessageModel, status: AiStreamStatus) {
  assistant.content = status.content || assistant.content
  if (status.sources) {
    assistant.sources = status.sources
    assistant.citations = parseCitations(status.sources)
  }
  if (status.graphNodes) assistant.graphNodes = status.graphNodes
  if (status.graphEdges) assistant.graphEdges = status.graphEdges
  if (status.model) assistant.model = status.model
  if (status.fallback !== undefined) assistant.fallback = status.fallback
  if (status.dataTimeRange) assistant.dataTimeRange = status.dataTimeRange
  if (status.dataScope) assistant.dataScope = status.dataScope
  if (status.sequence > lastSeq.value) lastSeq.value = status.sequence
}

async function recoverStream() {
  if (disposed || !currentRequestId.value || recoveringStream) return
  const requestId = currentRequestId.value
  recoveringStream = true
  try {
    const status = await getAiStreamStatus(requestId)
    if (disposed || requestId !== currentRequestId.value) return
    let assistant = findActiveAssistant()
    if (!assistant) {
      if (!status.content) {
        if (
          status.status === 'COMPLETED'
          || status.status === 'FAILED'
          || status.status === 'CANCELLED'
        ) finishStream()
        return
      }
      assistant = createAssistantMessage('上次会话')
      messages.value.push(assistant)
      activeAssistantId.value = assistant.id
    }
    applyRecoveredStatus(assistant, status)
    if (status.status === 'COMPLETED') {
      assistant.status = 'done'
      if (assistant.elapsedMs == null) assistant.elapsedMs = Date.now() - assistant.createdAt
      finishStream()
    } else if (status.status === 'FAILED') {
      assistant.status = 'error'
      assistant.error = status.message || 'AI 请求失败'
      finishStream()
    } else if (status.status === 'CANCELLED') {
      assistant.status = 'error'
      assistant.error = assistant.content
        ? '生成已取消，已保留部分内容。'
        : '生成已取消。'
      finishStream()
    } else {
      assistant.status = 'streaming'
      streaming.value = true
      startStreamFallback()
    }
  } catch { /* 状态可能已过期，保留当前页面内容 */ }
  finally { recoveringStream = false }
}

function finishStream() {
  streaming.value = false
  activeAssistantId.value = null
  stopStreamFallback()
  currentRequestId.value = ''
  sessionStorage.removeItem(REQUEST_KEY)
}

function startStreamFallback() {
  if (streamPollTimer !== null) return
  streamPollTimer = window.setInterval(() => {
    if (!streaming.value || !currentRequestId.value) {
      stopStreamFallback()
      return
    }
    void recoverStream()
  }, 2500)
}

function stopStreamFallback() {
  if (streamPollTimer !== null) {
    window.clearInterval(streamPollTimer)
    streamPollTimer = null
  }
}

function clearConnectingTimer() {
  if (connectingTimer !== null) {
    window.clearTimeout(connectingTimer)
    connectingTimer = null
  }
}

function readRecord(value: unknown): Record<string, unknown> | null {
  if (typeof value !== 'object' || value === null) return null
  return value as Record<string, unknown>
}

function readString(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value : null
}

function readBoolean(value: unknown): boolean | null {
  return typeof value === 'boolean' ? value : null
}

function readSources(value: unknown): string[] | null {
  if (!Array.isArray(value)) return null
  return value.filter((item): item is string => typeof item === 'string')
}

function readNodes(value: unknown): KnowledgeGraphNode[] | null {
  if (!Array.isArray(value)) return null
  return value.filter(isGraphNode)
}

function readEdges(value: unknown): KnowledgeGraphEdge[] | null {
  if (!Array.isArray(value)) return null
  return value.filter(isGraphEdge)
}

function isGraphNode(value: unknown): value is KnowledgeGraphNode {
  const record = readRecord(value)
  if (!record) return false
  return typeof record.id === 'string'
    && typeof record.name === 'string'
    && typeof record.type === 'string'
}

function isGraphEdge(value: unknown): value is KnowledgeGraphEdge {
  const record = readRecord(value)
  if (!record) return false
  return typeof record.source === 'string'
    && typeof record.target === 'string'
    && typeof record.type === 'string'
}

function isTimeoutError(error: unknown): boolean {
  return readRecord(error)?.code === 'ECONNABORTED'
}

function errorText(error: unknown, fallback: string): string {
  const record = readRecord(error)
  if (!record) return fallback
  const response = readRecord(record.response)
  const data = response ? readRecord(response.data) : null
  const serverMessage = data?.message ?? data?.msg
  if (typeof serverMessage === 'string' && serverMessage.trim()) return serverMessage.trim()
  if (record.code === 'ERR_NETWORK') return '网络异常，请检查连接后重试'
  const message = readString(record.message)
  if (message) return message
  return fallback
}

function concise(text: string): string {
  return text.length > 90 ? `${text.slice(0, 90)}…` : text
}

function queryErrorMessage(error: unknown): string {
  if (isTimeoutError(error)) return '请求超时，建议使用流式回答'
  return concise(errorText(error, '回答生成失败，请稍后重试'))
}

function streamErrorMessage(error: unknown): string {
  if (isTimeoutError(error)) return '请求超时，请检查网络后重试'
  return concise(errorText(error, '流式请求失败，请稍后重试'))
}

async function copyMessage(message: ChatMessageModel) {
  const text = message.content
  if (!text.trim()) return
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
      ElMessage.success('已复制回答')
      return
    }
  } catch { /* 回退到旧版复制 */ }
  if (legacyCopy(text)) ElMessage.success('已复制回答')
  else ElMessage.warning('复制失败，请手动选择文本复制')
}

function legacyCopy(text: string): boolean {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.top = '-1000px'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  let copied = false
  try {
    copied = document.execCommand('copy')
  } catch {
    copied = false
  }
  textarea.remove()
  return copied
}

function openGraph(message: ChatMessageModel) {
  graphNodes.value = message.graphNodes
  graphEdges.value = message.graphEdges
  const questionText = message.question?.trim()
  graphTitle.value = questionText
    ? `知识图谱 · ${truncateText(questionText, 24)}`
    : '知识图谱'
  graphVisible.value = true
}

function truncateText(text: string, maxLength: number): string {
  return text.length > maxLength ? `${text.slice(0, maxLength)}…` : text
}

async function refreshKnowledge() {
  try {
    knowledge.value = await getAiKnowledgeStatus()
    await maybeResumeKnowledgePolling()
  } catch { /* 权限或后端旧版本不支持时不影响问答 */ }
}

async function refreshFiles() {
  filesLoading.value = true
  try {
    knowledgeFiles.value = await listAiKnowledgeFiles()
  } catch { /* 权限错误不阻塞 AI 问答 */ }
  finally { filesLoading.value = false }
}

async function maybeResumeKnowledgePolling() {
  if (disposed || knowledgePollActive) return
  const taskId = knowledge.value?.lastTaskId
  const status = knowledge.value?.lastTaskStatus
  if (!taskId || (status !== 'RUNNING' && status !== 'QUEUED')) return
  try {
    const task = await getAiKnowledgeTask(taskId)
    if (disposed || knowledgePollActive) return
    knowledgeTask.value = task
    if (task.status === 'RUNNING' || task.status === 'QUEUED') {
      startKnowledgePolling(taskId)
    }
  } catch { /* 恢复任务状态失败时等待手动刷新 */ }
}

async function indexKnowledge() {
  if (indexingKnowledge.value || knowledgePollActive) return
  indexingKnowledge.value = true
  try {
    const task = await indexAiKnowledge()
    knowledgeTask.value = task
    ElMessage.success('知识库索引任务已提交')
    startKnowledgePolling(task.taskId)
  } catch {
    indexingKnowledge.value = false
  }
}

function startKnowledgePolling(taskId: string) {
  if (disposed || knowledgePollActive) return
  knowledgePollActive = true
  knowledgePollTaskId = taskId
  knowledgePollAttempts = 0
  indexingKnowledge.value = true
  scheduleKnowledgePoll()
}

function scheduleKnowledgePoll() {
  if (knowledgePollTimer !== null) window.clearTimeout(knowledgePollTimer)
  knowledgePollTimer = window.setTimeout(() => { void runKnowledgePoll() }, 1000)
}

async function runKnowledgePoll() {
  knowledgePollTimer = null
  if (disposed || !knowledgePollActive) return
  knowledgePollAttempts += 1
  try {
    const task = await getAiKnowledgeTask(knowledgePollTaskId)
    if (disposed || !knowledgePollActive) return
    knowledgeTask.value = task
    if (task.status === 'COMPLETED' || task.status === 'FAILED') {
      stopKnowledgePolling()
      await refreshKnowledge()
      await refreshFiles()
      if (task.status === 'FAILED') {
        ElMessage.error(task.message || '知识库索引失败')
      } else {
        ElMessage.success('知识库索引完成')
      }
      return
    }
  } catch {
    if (disposed || !knowledgePollActive) return
  }
  if (knowledgePollAttempts >= 60) {
    stopKnowledgePolling()
    ElMessage.info('索引任务仍在执行，可稍后刷新')
    return
  }
  scheduleKnowledgePoll()
}

function stopKnowledgePolling() {
  knowledgePollActive = false
  knowledgePollTaskId = ''
  knowledgePollAttempts = 0
  if (knowledgePollTimer !== null) {
    window.clearTimeout(knowledgePollTimer)
    knowledgePollTimer = null
  }
  indexingKnowledge.value = false
}

async function uploadKnowledgeFile(file: File, scope: 'PUBLIC' | 'PRIVATE') {
  if (fileBusy.value) return
  fileBusy.value = true
  try {
    await uploadAiKnowledgeFile(file, scope)
    ElMessage.success('知识文件已上传并开始索引')
    await refreshFiles()
    scheduleFileRefresh()
  } catch { /* 请求拦截器已提示 */ }
  finally { fileBusy.value = false }
}

async function replaceKnowledgeFile(documentId: number, file: File) {
  if (fileBusy.value) return
  fileBusy.value = true
  try {
    await replaceAiKnowledgeFile(documentId, file)
    ElMessage.success('知识文件已更新并重新索引')
    await refreshFiles()
    scheduleFileRefresh()
  } catch { /* 请求拦截器已提示 */ }
  finally { fileBusy.value = false }
}

async function removeKnowledgeFile(documentId: number) {
  try {
    await ElMessageBox.confirm(
      '删除后会同步清理文档、向量和图谱索引。',
      '删除知识文件',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      }
    )
  } catch { return }
  if (fileBusy.value) return
  fileBusy.value = true
  try {
    await deleteAiKnowledgeFile(documentId)
    ElMessage.success('知识文件已删除')
    await refreshFiles()
  } catch { /* 请求拦截器已提示 */ }
  finally { fileBusy.value = false }
}

async function downloadKnowledgeFile(file: AiKnowledgeFile) {
  if (fileBusy.value) return
  fileBusy.value = true
  try {
    const response = await downloadAiKnowledgeFile(file.documentId)
    const blob = response.data instanceof Blob ? response.data : new Blob([response.data])
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = file.name
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    runLater(() => URL.revokeObjectURL(url), 1500)
  } catch { /* 请求拦截器已提示 */ }
  finally { fileBusy.value = false }
}

function scheduleFileRefresh() {
  clearFileRefreshTimers()
  fileRefreshTimers = [2500, 6000].map(delay => (
    window.setTimeout(() => { void refreshFiles() }, delay)
  ))
}

function clearFileRefreshTimers() {
  fileRefreshTimers.forEach(timer => window.clearTimeout(timer))
  fileRefreshTimers = []
}

function runLater(callback: () => void, delay: number) {
  const timer = window.setTimeout(() => {
    transientTimers.delete(timer)
    callback()
  }, delay)
  transientTimers.add(timer)
}

async function createPlan(questionText: string) {
  const text = questionText.trim()
  if (!text || planning.value) return
  planning.value = true
  try {
    plan.value = await _createPlan({
      question: text,
      days: days.value,
      sessionId: sessionId.value
    })
  } catch { /* 请求拦截器已提示 */ }
  finally { planning.value = false }
}

async function confirmPlan() {
  if (!plan.value || confirming.value) return
  confirming.value = true
  try {
    plan.value = await _confirmPlan(plan.value.planId)
    ElMessage.success('建议已确认并留存草稿，未自动修改业务数据')
  } catch { /* 请求拦截器已提示 */ }
  finally { confirming.value = false }
}
</script>

<style scoped>
.header-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.conn-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  color: var(--mes-text-muted, var(--lux-text-faint));
  font-size: 12px;
  font-weight: 600;
  line-height: 1.5;
  white-space: nowrap;
  background: var(--lux-surface-soft);
  border: 1px solid var(--lux-line);
  border-radius: 999px;
}

.conn-pill.is-online {
  color: var(--lux-jade-600);
  background: var(--lux-jade-50);
  border-color: var(--lux-jade-line);
}

.conn-pill.is-connecting {
  color: var(--lux-ember-600);
  background: var(--lux-ember-50);
  border-color: var(--lux-ember-line);
}

.conn-pill.is-offline {
  color: var(--mes-text-muted, var(--lux-text-faint));
  background: var(--lux-surface-soft);
  border-color: var(--lux-line);
}

.conn-dot {
  flex: none;
  width: 7px;
  height: 7px;
  background: currentcolor;
  border-radius: 50%;
}

.conn-pill.is-online .conn-dot {
  box-shadow: 0 0 0 3px rgb(63 143 121 / 12%);
}

.conn-pill.is-connecting .conn-dot {
  box-shadow: 0 0 0 3px rgb(184 117 46 / 12%);
}

.session-chip {
  padding: 3px 9px;
  overflow: hidden;
  color: var(--mes-text-muted, var(--lux-text-faint));
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  line-height: 1.6;
  white-space: nowrap;
  text-overflow: ellipsis;
  background: var(--mes-bg-soft, var(--lux-surface-soft));
  border: 1px solid var(--mes-border-light, var(--lux-line-soft));
  border-radius: 999px;
}

.header-meta :deep(.el-button + .el-button) {
  margin-left: 0;
}

.ai-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 400px;
  gap: 18px;
  align-items: start;
  min-width: 0;
}

.chat-panel {
  display: flex;
  min-width: 0;
  flex-direction: column;
  overflow: hidden;
  background: var(--mes-bg-card, #fff);
  border: 1px solid var(--mes-border, var(--lux-line));
  border-radius: 16px;
  box-shadow: var(--mes-shadow-sm, 0 6px 20px rgb(20 40 70 / 5%));
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 17px 22px 15px;
  background: linear-gradient(180deg, #fff 0%, var(--lux-surface-soft) 100%);
  border-bottom: 1px solid var(--mes-border-light, var(--lux-line-soft));
}

.chat-heading {
  min-width: 0;
}

.chat-heading h2 {
  color: var(--mes-text-primary, var(--lux-text-strong));
  font-size: 16px;
  font-weight: 700;
  line-height: 1.4;
  letter-spacing: -0.1px;
}

.chat-heading p {
  margin-top: 3px;
  color: var(--mes-text-muted, var(--lux-text-faint));
  font-size: 12px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.streaming-badge {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  color: var(--mes-primary-hover, var(--lux-gold-700));
  font-size: 12px;
  font-weight: 600;
  line-height: 1.6;
  white-space: nowrap;
  background: var(--mes-primary-soft, #f7f1e4);
  border: 1px solid var(--lux-line-strong);
  border-radius: 999px;
}

.streaming-dot {
  width: 6px;
  height: 6px;
  background: var(--mes-primary, var(--lux-gold-700));
  border-radius: 50%;
  animation: streaming-pulse 1.2s ease-in-out infinite;
}

.chat-log {
  display: flex;
  height: clamp(300px, calc(100vh - 510px), 620px);
  min-width: 0;
  flex-direction: column;
  gap: 18px;
  padding: 22px;
  overflow-x: hidden;
  overflow-y: auto;
  background:
    radial-gradient(circle at 12% 0%, rgb(234 220 192 / 42%) 0%, transparent 42%),
    linear-gradient(180deg, var(--lux-surface-soft) 0%, var(--lux-surface-soft) 48%, var(--lux-surface-soft) 100%);
}

.chat-log :deep(.chat-message),
.chat-log :deep(.message-main) {
  min-width: 0;
}

.chat-log.is-streaming :deep(.action-row .el-button[aria-label='重新生成回答']) {
  pointer-events: none;
  opacity: 0.45;
}

.chat-log.is-streaming :deep(.error-retry) {
  pointer-events: none;
  opacity: 0.45;
}

.welcome-card {
  width: 100%;
  max-width: 660px;
  margin: auto;
  padding: 28px;
  background: linear-gradient(180deg, #fff 0%, var(--lux-surface-soft) 100%);
  border: 1px solid var(--lux-porcelain-200);
  border-radius: 16px;
  box-shadow: var(--mes-shadow-sm, 0 6px 20px rgb(20 40 70 / 5%));
  animation: welcome-in 0.28s ease both;
}

.welcome-icon {
  display: grid;
  width: 42px;
  height: 42px;
  margin-bottom: 14px;
  color: #fff;
  background: linear-gradient(135deg, var(--mes-primary, var(--lux-gold-700)), var(--lux-gold-500));
  border-radius: 12px;
  box-shadow: 0 8px 18px rgb(198 169 110 / 22%);
  place-items: center;
}

.welcome-card h2 {
  color: var(--mes-text-primary, var(--lux-text-strong));
  font-size: 19px;
  font-weight: 700;
  line-height: 1.4;
  letter-spacing: -0.2px;
}

.welcome-subtitle {
  margin-top: 7px;
  color: var(--mes-text-secondary, var(--lux-text-muted));
  font-size: 13px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.suggestion-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 20px;
}

.suggestion-chip {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: flex-start;
  gap: 8px;
  padding: 11px 13px;
  color: var(--mes-primary-hover, var(--lux-gold-700));
  font: inherit;
  font-size: 13px;
  line-height: 1.55;
  text-align: left;
  overflow-wrap: anywhere;
  background: linear-gradient(180deg, var(--lux-surface-soft) 0%, #f7f1e4 100%);
  border: 1px solid var(--lux-line-strong);
  border-radius: 11px;
  cursor: pointer;
  transition:
    transform var(--mes-transition, 0.22s ease),
    box-shadow var(--mes-transition-fast, 0.16s ease),
    border-color var(--mes-transition-fast, 0.16s ease),
    background-color var(--mes-transition-fast, 0.16s ease);
}

.suggestion-chip:hover {
  border-color: var(--lux-gold-500);
  box-shadow: 0 7px 18px rgb(198 169 110 / 12%);
  transform: translateY(-1px);
}

.suggestion-chip:active {
  transform: translateY(0);
}

.suggestion-chip:focus-visible {
  outline: 2px solid rgb(198 169 110 / 45%);
  outline-offset: 2px;
}

.suggestion-icon {
  flex: none;
  margin-top: 2px;
  font-size: 14px;
  opacity: 0.8;
}

.composer {
  padding: 16px 20px 14px;
  background: var(--mes-bg-card, #fff);
  border-top: 1px solid var(--mes-border-light, var(--lux-line-soft));
}

.composer-controls {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px 18px;
  margin-top: 12px;
  min-width: 0;
}

.control-group {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.control-label {
  color: var(--mes-text-muted, var(--lux-text-faint));
  font-size: 12px;
  white-space: nowrap;
}

.days-input {
  width: 104px;
}

.composer-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-left: auto;
}

.composer-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.composer-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 6px 14px;
  margin-top: 10px;
  color: var(--mes-text-placeholder, var(--lux-text-faint));
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
  line-height: 1.5;
}

.side-panel {
  position: sticky;
  top: 0;
  align-self: start;
  min-width: 0;
}

.side-tabs {
  min-width: 0;
  overflow: hidden;
  background: var(--mes-bg-card, #fff);
  border: 1px solid var(--mes-border, var(--lux-line));
  border-radius: 16px;
  box-shadow: var(--mes-shadow-xs, 0 2px 8px rgb(20 40 70 / 4%));
}

.side-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 8px 18px 0;
  background: linear-gradient(180deg, #fff 0%, var(--lux-surface-soft) 100%);
  border-bottom: 1px solid var(--mes-border-light, var(--lux-line-soft));
}

.side-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.side-tabs :deep(.el-tabs__item) {
  height: 42px;
  color: var(--mes-text-secondary, var(--lux-text-muted));
  font-size: 13px;
  font-weight: 600;
}

.side-tabs :deep(.el-tabs__item.is-active) {
  color: var(--mes-primary, var(--lux-gold-700));
}

.side-tabs :deep(.el-tabs__content) {
  padding: 16px 18px 18px;
}

@keyframes welcome-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes streaming-pulse {
  0%,
  100% {
    opacity: 0.35;
    transform: scale(0.85);
  }

  50% {
    opacity: 1;
    transform: scale(1);
  }
}

@media (max-width: 1200px) {
  .ai-workspace {
    grid-template-columns: minmax(0, 1fr);
  }

  .side-panel {
    position: static;
    top: auto;
    width: 100%;
  }
}

@media (max-width: 900px) {
  .chat-header {
    padding: 15px 18px 13px;
  }

  .chat-log {
    padding: 18px;
  }

  .welcome-card {
    padding: 22px;
  }
}

@media (max-width: 640px) {
  .chat-header {
    align-items: flex-start;
    flex-wrap: wrap;
    padding: 14px 16px 12px;
  }

  .chat-log {
    height: 420px;
    padding: 14px;
    gap: 14px;
  }

  .welcome-card {
    padding: 18px;
  }

  .welcome-card h2 {
    font-size: 17px;
  }

  .suggestion-grid {
    grid-template-columns: minmax(0, 1fr);
    margin-top: 16px;
  }

  .composer {
    padding: 14px 14px 12px;
  }

  .composer-controls {
    align-items: stretch;
    flex-direction: column;
    gap: 10px;
  }

  .control-group {
    justify-content: space-between;
  }

  .composer-actions {
    width: 100%;
    margin-left: 0;
    flex-direction: column;
    align-items: stretch;
  }

  .composer-actions :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }

  .composer-hint {
    flex-direction: column;
    align-items: flex-start;
  }

  .side-tabs :deep(.el-tabs__content) {
    padding: 14px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .welcome-card,
  .streaming-dot {
    animation: none;
  }

  .suggestion-chip {
    transition: none;
  }

  .suggestion-chip:hover {
    transform: none;
  }
}
</style>
