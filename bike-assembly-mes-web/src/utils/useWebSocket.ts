/** 模块级实时通知连接；业务数据始终通过页面原有 API 查询。 */
export interface WsMessage {
  bizType: string
  eventType: string
  timestamp: string
  data: Record<string, unknown>
}

type MessageHandler = (msg: WsMessage) => void | Promise<void>
type ConnectedHandler = () => void | Promise<void>

export interface AiWsMessage extends WsMessage {
  data: Record<string, unknown> & {
    requestId?: string
    sessionId?: string
    delta?: string
    message?: string
    planId?: string
    status?: string
    seq?: number
    sources?: string[]
    dataTimeRange?: string
    model?: string
    fallback?: boolean
  }
}

type ConnectionHandler = (value: boolean) => void

let ws: WebSocket | null = null
let heartbeatTimer: number | null = null
let pongTimer: number | null = null
let reconnectTimer: number | null = null
let reconnectAttempts = 0
let manuallyClosed = true
let connected = false

const businessTypes = new Set(['ANDON', 'WORK_ORDER', 'QUALITY', 'REPAIR', 'INVENTORY', 'AI'])
const listeners = new Map<string, Set<MessageHandler>>()
const connectedListeners = new Set<ConnectedHandler>()
const connectionListeners = new Set<ConnectionHandler>()

function setConnected(value: boolean): void {
  if (connected === value) return
  connected = value
  connectionListeners.forEach(handler => {
    try { handler(value) } catch { /* observer errors do not affect the connection */ }
  })
}

function wsUrl(token: string): string {
  const url = new URL(import.meta.env.VITE_API_BASE_URL || '/', window.location.origin + '/')
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  url.pathname = url.pathname.replace(/\/+$/, '') + '/ws'
  url.search = ''
  url.hash = ''
  url.searchParams.set('token', token)
  return url.toString()
}

function isMessage(value: unknown): value is WsMessage {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const msg = value as Record<string, unknown>
  return typeof msg.bizType === 'string' && businessTypes.has(msg.bizType)
    && typeof msg.eventType === 'string' && msg.eventType.length > 0
    && typeof msg.timestamp === 'string' && msg.timestamp.length > 0
    && !!msg.data && typeof msg.data === 'object' && !Array.isArray(msg.data)
}

function invoke(handler: () => void | Promise<void>): void {
  try {
    Promise.resolve(handler()).catch(() => { /* 订阅失败不影响其他页面 */ })
  } catch { /* 同步订阅失败不影响其他页面 */ }
}

function clearHeartbeat(): void {
  if (heartbeatTimer !== null) window.clearInterval(heartbeatTimer)
  if (pongTimer !== null) window.clearTimeout(pongTimer)
  heartbeatTimer = null
  pongTimer = null
}

function scheduleReconnect(): void {
  if (manuallyClosed || reconnectTimer !== null || !localStorage.getItem('token')) return
  const delay = Math.min(1000 * 2 ** reconnectAttempts, 30000)
  reconnectAttempts = Math.min(reconnectAttempts + 1, 5)
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = null
    connect()
  }, delay)
}

/** 先让连接失效，避免同步或延迟的 close/error 影响下一次连接。 */
function retire(socket: WebSocket): void {
  if (ws !== socket) return
  ws = null
  setConnected(false)
  clearHeartbeat()
  try { socket.close() } catch { /* 连接已不可用，继续安排恢复 */ }
  scheduleReconnect()
}

export function connect(): void {
  const token = localStorage.getItem('token')
  if (!token) {
    disconnect()
    return
  }
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return
  if (reconnectTimer !== null) window.clearTimeout(reconnectTimer)
  reconnectTimer = null
  clearHeartbeat()
  manuallyClosed = false
  let socket: WebSocket
  try {
    socket = new WebSocket(wsUrl(token))
    ws = socket
  } catch {
    scheduleReconnect()
    return
  }

  socket.onopen = () => {
    if (ws !== socket) return
    reconnectAttempts = 0
    clearHeartbeat()
    setConnected(true)
    heartbeatTimer = window.setInterval(() => {
      if (ws !== socket || socket.readyState !== WebSocket.OPEN) return
      if (!localStorage.getItem('token')) {
        disconnect()
        return
      }
      try {
        socket.send('ping')
        pongTimer = window.setTimeout(() => retire(socket), 10000)
      } catch {
        retire(socket)
      }
    }, 30000)
    connectedListeners.forEach(handler => invoke(handler))
  }

  socket.onmessage = event => {
    if (ws !== socket) return
    if (event.data === 'pong') {
      if (pongTimer !== null) window.clearTimeout(pongTimer)
      pongTimer = null
      return
    }
    if (typeof event.data !== 'string') return
    try {
      const data: unknown = JSON.parse(event.data)
      if (!isMessage(data)) return
      listeners.get(data.bizType)?.forEach(handler => invoke(() => handler(data)))
    } catch { /* 忽略不符合通知协议的消息 */ }
  }

  socket.onclose = () => retire(socket)
  socket.onerror = () => retire(socket)
}

export function disconnect(): void {
  manuallyClosed = true
  const socket = ws
  setConnected(false)
  ws = null
  if (reconnectTimer !== null) window.clearTimeout(reconnectTimer)
  reconnectTimer = null
  reconnectAttempts = 0
  clearHeartbeat()
  try { socket?.close() } catch { /* 定时器与引用已清理 */ }
}

/** 按业务类型订阅，页面卸载时调用返回的注销函数。 */
export function onWsMessage(bizType: string, handler: MessageHandler): () => void {
  if (!listeners.has(bizType)) listeners.set(bizType, new Set())
  listeners.get(bizType)!.add(handler)
  return () => {
    const handlers = listeners.get(bizType)
    handlers?.delete(handler)
    if (!handlers?.size) listeners.delete(bizType)
  }
}

/** 建连及重连后重新查询，补齐断线期间未收到的实时提示。 */
export function onWsConnected(handler: ConnectedHandler): () => void {
  connectedListeners.add(handler)
  return () => { connectedListeners.delete(handler) }
}

export function onWsConnectionChange(handler: ConnectionHandler): () => void {
  connectionListeners.add(handler)
  handler(connected)
  return () => connectionListeners.delete(handler)
}

export function useWebSocket() {
  return {
    connect,
    disconnect,
    onWsMessage,
    onWsConnectionChange,
    onAiMessage: (handler: (msg: AiWsMessage) => void) => onWsMessage('AI', handler)
  }
}
