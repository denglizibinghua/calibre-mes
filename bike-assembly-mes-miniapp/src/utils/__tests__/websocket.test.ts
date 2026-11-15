import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

class SocketStub {
  open = () => {}
  message = (_: { data: string }) => {}
  closed = () => {}
  error = () => {}
  send = vi.fn()
  close = vi.fn()
  onOpen(callback: () => void) { this.open = callback }
  onMessage(callback: (event: { data: string }) => void) { this.message = callback }
  onClose(callback: () => void) { this.closed = callback }
  onError(callback: () => void) { this.error = callback }
}

let sockets: SocketStub[]
let storage: Record<string, string>
let ws: typeof import('../websocket')
const event = (bizType = 'ANDON') => ({ bizType, eventType: 'UPDATED', timestamp: '2026-10-31T12:00:00', data: { id: 1 } })

beforeEach(async () => {
  vi.resetModules()
  vi.useFakeTimers()
  storage = { accessToken: 'first-token' }
  sockets = []
  vi.stubGlobal('uni', {
    getStorageSync: vi.fn((key: string) => storage[key] || ''),
    connectSocket: vi.fn(() => { const socket = new SocketStub(); sockets.push(socket); return socket }),
    sendSocketMessage: vi.fn(),
  })
  ws = await import('../websocket')
})

afterEach(() => {
  ws.closeWs()
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('SocketTask 连接与恢复', () => {
  it('无 token 抛出本地认证错误且不连接，兼容旧 token key', () => {
    storage = {}
    expect(() => ws.connectWs(vi.fn())).toThrow('登录已失效')
    vi.advanceTimersByTime(60_000)
    expect(sockets).toHaveLength(0)
    storage.token = 'legacy'
    ws.connectWs(vi.fn())
    expect(uni.connectSocket).toHaveBeenCalledWith(expect.objectContaining({ url: expect.stringContaining('token=legacy') }))
  })

  it('只通过当前 SocketTask 发送30秒心跳，纯文本 pong 消除10秒超时', () => {
    ws.connectWs(vi.fn())
    sockets[0].open()
    vi.advanceTimersByTime(29_999)
    expect(sockets[0].send).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    expect(sockets[0].send).toHaveBeenCalledWith(expect.objectContaining({ data: 'ping' }))
    sockets[0].message({ data: 'pong' })
    vi.advanceTimersByTime(10_000)
    expect(sockets[0].close).not.toHaveBeenCalled()
    expect(uni.sendSocketMessage).not.toHaveBeenCalled()
  })

  it('缺少 pong 在10秒后关闭并退避重连', () => {
    ws.connectWs(vi.fn())
    sockets[0].open()
    vi.advanceTimersByTime(40_000)
    expect(sockets[0].close).toHaveBeenCalledOnce()
    vi.advanceTimersByTime(999)
    expect(sockets).toHaveLength(1)
    vi.advanceTimersByTime(1)
    expect(sockets).toHaveLength(2)
  })

  it('断线按1/2/4/8/16/30秒重试，error与close不重复排队，open重置退避', () => {
    ws.connectWs(vi.fn())
    for (const delay of [1000, 2000, 4000, 8000, 16000, 30000, 30000]) {
      const current = sockets[sockets.length - 1]
      current.error()
      current.closed()
      const count = sockets.length
      vi.advanceTimersByTime(delay - 1)
      expect(sockets).toHaveLength(count)
      vi.advanceTimersByTime(1)
      expect(sockets).toHaveLength(count + 1)
    }
    sockets.at(-1)!.open()
    sockets.at(-1)!.closed()
    vi.advanceTimersByTime(1000)
    expect(sockets).toHaveLength(9)
  })

  it('重连读取最新 token，无 token 后停止任务并清理订阅', () => {
    const handler = vi.fn()
    ws.connectWs(handler)
    sockets[0].closed()
    storage.accessToken = 'next token'
    vi.advanceTimersByTime(1000)
    expect(uni.connectSocket).toHaveBeenLastCalledWith(expect.objectContaining({ url: expect.stringContaining('token=next%20token') }))
    sockets[1].closed()
    storage = {}
    vi.advanceTimersByTime(60_000)
    expect(sockets).toHaveLength(2)
    expect(vi.getTimerCount()).toBe(0)
    storage.token = 'new-account'
    ws.connectWs(vi.fn())
    sockets[2].message({ data: JSON.stringify(event()) })
    expect(handler).not.toHaveBeenCalled()
  })

  it('主动关闭后旧连接 open/message/error/close 和 send失败均不能影响新连接', () => {
    const oldHandler = vi.fn()
    ws.connectWs(oldHandler)
    const old = sockets[0]
    old.open()
    vi.advanceTimersByTime(30_000)
    const failedSend = old.send.mock.calls[0][0].fail
    ws.closeWs()
    const handler = vi.fn()
    ws.connectWs(handler)
    sockets[1].open()
    old.open(); old.message({ data: JSON.stringify(event()) }); old.error(); old.closed(); failedSend()
    sockets[1].message({ data: JSON.stringify(event()) })
    vi.advanceTimersByTime(1000)
    expect(sockets).toHaveLength(2)
    expect(oldHandler).not.toHaveBeenCalled()
    expect(handler).toHaveBeenCalledOnce()
    ws.closeWs()
    vi.advanceTimersByTime(120_000)
    expect(sockets).toHaveLength(2)
    expect(vi.getTimerCount()).toBe(0)
  })

  it('底层同步连接失败保留订阅并重试，发送失败关闭当前连接', () => {
    vi.mocked(uni.connectSocket).mockImplementationOnce(() => { throw new Error('offline') })
    const handler = vi.fn()
    expect(() => ws.connectWs(handler)).toThrow('实时连接暂不可用')
    vi.advanceTimersByTime(1000)
    sockets[0].open()
    sockets[0].message({ data: JSON.stringify(event()) })
    expect(handler).toHaveBeenCalledOnce()
    vi.advanceTimersByTime(30_000)
    sockets[0].send.mock.calls[0][0].fail()
    expect(sockets[0].close).toHaveBeenCalledOnce()
    vi.advanceTimersByTime(1000)
    expect(sockets).toHaveLength(2)
  })
})

describe('独立订阅与消息边界', () => {
  it('异步订阅拒绝被隔离，不影响消息与连接恢复的其他订阅者', async () => {
    const handler = vi.fn(), connected = vi.fn()
    ws.connectWs(async () => { throw new Error('async message subscriber') })
    ws.onWsMessage('ANDON', handler)
    ws.onWsConnected(async () => { throw new Error('async connected subscriber') })
    ws.onWsConnected(connected)
    sockets[0].open()
    sockets[0].message({ data: JSON.stringify(event()) })
    await Promise.resolve()
    expect(handler).toHaveBeenCalledOnce()
    expect(connected).toHaveBeenCalledOnce()
  })

  it('多个兼容回调都收到消息，同一连接不重复创建，独立业务订阅可取消', () => {
    const first = vi.fn(), second = vi.fn(), andon = vi.fn(), repair = vi.fn()
    expect(ws.connectWs(first)).toBe(ws.connectWs(second))
    const off = ws.onWsMessage('ANDON', andon)
    ws.onWsMessage('REPAIR', repair)
    sockets[0].message({ data: JSON.stringify(event()) })
    expect(first).toHaveBeenCalledOnce()
    expect(second).toHaveBeenCalledOnce()
    expect(andon).toHaveBeenCalledOnce()
    expect(repair).not.toHaveBeenCalled()
    off()
    sockets[0].message({ data: JSON.stringify(event()) })
    expect(andon).toHaveBeenCalledOnce()
    ws.closeWs()
    ws.connectWs(vi.fn())
    sockets[1].message({ data: JSON.stringify(event('REPAIR')) })
    expect(repair).not.toHaveBeenCalled()
  })

  it('忽略未知及非法消息，订阅异常不阻断其他订阅者', () => {
    const handler = vi.fn()
    ws.connectWs(() => { throw new Error('subscriber') })
    ws.onWsMessage('ANDON', handler)
    for (const data of ['pong', '{', 'null', JSON.stringify(event('UNKNOWN')), JSON.stringify({ ...event(), timestamp: [] }), JSON.stringify({ ...event(), data: [] }), JSON.stringify({ ...event(), eventType: 1 })]) {
      sockets[0].message({ data })
    }
    expect(handler).not.toHaveBeenCalled()
    sockets[0].message({ data: JSON.stringify(event()) })
    expect(handler).toHaveBeenCalledOnce()
  })

  it('每次成功建立连接通知独立监听者，取消或close后停止补查', () => {
    const connected = vi.fn(), other = vi.fn()
    const off = ws.onWsConnected(connected)
    ws.onWsConnected(() => { throw new Error('refresh') })
    ws.onWsConnected(other)
    ws.connectWs(vi.fn())
    sockets[0].open()
    expect(connected).toHaveBeenCalledOnce()
    expect(other).toHaveBeenCalledOnce()
    off()
    sockets[0].closed()
    vi.advanceTimersByTime(1000)
    sockets[1].open()
    expect(connected).toHaveBeenCalledOnce()
    expect(other).toHaveBeenCalledTimes(2)
    ws.closeWs()
    ws.connectWs(vi.fn())
    sockets[2].open()
    expect(other).toHaveBeenCalledTimes(2)
  })
})
