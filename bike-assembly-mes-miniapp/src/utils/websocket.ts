import { API_BASE_URL, STORAGE_KEYS } from '@/constants'

/** WebSocket 推送消息（与后端 WsMessageDTO 对齐） */
export interface WsMessage {
  bizType: string
  eventType: string
  timestamp: string
  data: Record<string, unknown>
}

type MessageHandler = (msg: WsMessage) => void | Promise<void>
const businessTypes = new Set(['ANDON', 'WORK_ORDER', 'QUALITY', 'REPAIR', 'INVENTORY'])
const subscribers = new Set<{ bizType?: string; handler: MessageHandler }>()
const connectedSubscribers = new Set<() => void | Promise<void>>()
let socket: UniApp.SocketTask | null = null
let generation = 0
let active = false
let retryAttempt = 0
let heartbeatTimer: ReturnType<typeof setInterval> | null = null
let pongTimer: ReturnType<typeof setTimeout> | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null

function accessToken(): string {
  try {
    return String(uni.getStorageSync(STORAGE_KEYS.ACCESS_TOKEN) || uni.getStorageSync(STORAGE_KEYS.TOKEN) || '')
  } catch {
    return ''
  }
}

function clearHeartbeat(): void {
  if (heartbeatTimer !== null) clearInterval(heartbeatTimer)
  if (pongTimer !== null) clearTimeout(pongTimer)
  heartbeatTimer = pongTimer = null
}

function safelyClose(task: UniApp.SocketTask | null): void {
  try { task?.close({}) } catch { /* 平台连接已释放时仍需继续清理。 */ }
}

function safelyNotify(callback: () => void | Promise<void>): void {
  try {
    Promise.resolve(callback()).catch(() => { /* 异步页面回调失败不阻断其他订阅。 */ })
  } catch { /* 单个页面回调失败不能阻断其他订阅。 */ }
}

function scheduleReconnect(): void {
  if (!active || reconnectTimer !== null) return
  if (!accessToken()) { closeWs(); return }
  const delay = Math.min(1000 * 2 ** retryAttempt++, 30_000)
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    if (!active) return
    if (!accessToken()) { closeWs(); return }
    try { openSocket() } catch { /* openSocket 已排入下一次退避重试。 */ }
  }, delay)
}

function validMessage(value: unknown): value is WsMessage {
  if (!value || typeof value !== 'object') return false
  const msg = value as WsMessage
  return typeof msg.bizType === 'string' && businessTypes.has(msg.bizType)
    && typeof msg.eventType === 'string' && msg.eventType.length > 0
    && typeof msg.timestamp === 'string' && msg.timestamp.length > 0
    && msg.data !== null && typeof msg.data === 'object' && !Array.isArray(msg.data)
}

function openSocket(): UniApp.SocketTask {
  const token = accessToken()
  if (!token) { closeWs(); throw new Error('登录已失效，请重新登录') }
  const currentGeneration = ++generation
  let task: UniApp.SocketTask
  try {
    task = uni.connectSocket({
      url: API_BASE_URL.replace(/^http/, 'ws').replace(/\/+$/, '') + '/ws?token=' + encodeURIComponent(token),
      complete: () => {},
    })
  } catch {
    scheduleReconnect()
    throw new Error('实时连接暂不可用，将自动重试')
  }
  socket = task
  const isCurrent = () => active && socket === task && generation === currentGeneration
  const fail = (close: boolean) => {
    if (!isCurrent()) return
    // 先失效，避免 close 同步触发的旧回调再清理新连接。
    socket = null
    generation++
    clearHeartbeat()
    if (close) safelyClose(task)
    scheduleReconnect()
  }
  task.onOpen(() => {
    if (!isCurrent()) return
    retryAttempt = 0
    clearHeartbeat()
    heartbeatTimer = setInterval(() => {
      if (!isCurrent()) return
      if (!accessToken()) { closeWs(); return }
      pongTimer = setTimeout(() => fail(true), 10_000)
      try { task.send({ data: 'ping', fail: () => fail(true) }) } catch { fail(true) }
    }, 30_000)
    for (const callback of [...connectedSubscribers]) {
      if (!isCurrent()) break
      if (connectedSubscribers.has(callback)) safelyNotify(callback)
    }
  })
  task.onMessage(({ data }) => {
    if (!isCurrent()) return
    if (data === 'pong') {
      if (pongTimer !== null) clearTimeout(pongTimer)
      pongTimer = null
      return
    }
    let message: unknown
    try { message = JSON.parse(String(data)) } catch { return }
    if (!validMessage(message)) return
    const msg = message
    for (const subscription of [...subscribers]) {
      if (!isCurrent()) break
      if (subscribers.has(subscription) && (!subscription.bizType || subscription.bizType === msg.bizType)) {
        safelyNotify(() => subscription.handler(msg))
      }
    }
  })
  task.onClose(() => fail(false))
  task.onError(() => fail(true))
  return task
}

/** 保留原 SocketTask 返回值，重复调用的每个回调均独立接收消息。 */
export function connectWs(onMessage?: MessageHandler): UniApp.SocketTask {
  if (!accessToken()) { closeWs(); throw new Error('登录已失效，请重新登录') }
  if (onMessage) subscribers.add({ handler: onMessage })
  if (socket) return socket
  active = true
  if (reconnectTimer !== null) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  return openSocket()
}

/** 独立业务订阅；closeWs 也会统一释放本次页面的全部订阅。 */
export function onWsMessage(bizType: string, handler: MessageHandler): () => void {
  const subscription = { bizType, handler }
  subscribers.add(subscription)
  return () => { subscribers.delete(subscription) }
}

/** 每次连接成功补查最新状态，不提供离线消息补发。 */
export function onWsConnected(handler: () => void | Promise<void>): () => void {
  connectedSubscribers.add(handler)
  return () => { connectedSubscribers.delete(handler) }
}

/** 页面隐藏、卸载和认证清理时停止重连并释放所有页面订阅。 */
export function closeWs(): void {
  active = false
  generation++
  const previous = socket
  socket = null
  clearHeartbeat()
  if (reconnectTimer !== null) clearTimeout(reconnectTimer)
  reconnectTimer = null
  retryAttempt = 0
  subscribers.clear()
  connectedSubscribers.clear()
  safelyClose(previous)
}
