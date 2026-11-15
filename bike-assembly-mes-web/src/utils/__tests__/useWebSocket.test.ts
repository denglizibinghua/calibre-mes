import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

class SocketStub {
  static CONNECTING = 0
  static OPEN = 1
  static CLOSED = 3
  static instances: SocketStub[] = []
  readyState = SocketStub.CONNECTING
  onopen: (() => void) | null = null
  onclose: (() => void) | null = null
  onerror: (() => void) | null = null
  onmessage: ((event: { data: unknown }) => void) | null = null
  send = vi.fn()
  // Deliberately delay onclose: browser callbacks can arrive after a new connection.
  close = vi.fn(() => { this.readyState = SocketStub.CLOSED })
  constructor(public url: string) { SocketStub.instances.push(this) }
  open() { this.readyState = SocketStub.OPEN; this.onopen?.() }
  closed() { this.readyState = SocketStub.CLOSED; this.onclose?.() }
  message(data: unknown) { this.onmessage?.({ data }) }
}

let client: typeof import('../useWebSocket')
const latest = () => SocketStub.instances[SocketStub.instances.length - 1]!
const notification = { bizType: 'ANDON', eventType: 'CREATED', timestamp: '2026-10-31T15:30:00', data: { id: 1 } }

beforeEach(async () => {
  vi.resetModules()
  vi.useFakeTimers()
  vi.stubGlobal('WebSocket', SocketStub)
  vi.stubEnv('VITE_API_BASE_URL', '')
  SocketStub.instances = []
  localStorage.clear()
  localStorage.setItem('token', 'access token')
  client = await import('../useWebSocket')
})

afterEach(() => {
  client.disconnect()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

describe('WebSocket addresses and authentication', () => {
  it.each([
    ['', '/ws'],
    ['/', '/ws'],
    ['/gateway', '/gateway/ws'],
    ['gateway/', '/gateway/ws'],
  ])('resolves %s against the current origin', (base, path) => {
    vi.stubEnv('VITE_API_BASE_URL', base)
    client.connect()
    const url = new URL(latest().url)
    expect(url.origin).toBe(window.location.origin.replace(/^http/, 'ws'))
    expect(url.pathname).toBe(path)
    expect(url.searchParams.get('token')).toBe('access token')
  })

  it.each([
    ['http://api.example.com/root/', 'ws://api.example.com/root/ws'],
    ['https://api.example.com/root', 'wss://api.example.com/root/ws'],
  ])('preserves deployment prefix for %s', (base, address) => {
    vi.stubEnv('VITE_API_BASE_URL', base)
    client.connect()
    expect(latest().url.split('?')[0]).toBe(address)
  })

  it('never opens or retries without an access token', () => {
    localStorage.removeItem('token')
    client.connect()
    vi.advanceTimersByTime(120000)
    expect(SocketStub.instances).toHaveLength(0)
  })

  it('uses WSS for an empty base URL on an HTTPS page', () => {
    vi.stubGlobal('window', { location: { origin: 'https://mes.example.com' } })
    client.connect()
    expect(new URL(latest().url).origin).toBe('wss://mes.example.com')
  })

  it('reads the latest token on retry and stops when credentials disappear', () => {
    client.connect()
    latest().closed()
    localStorage.setItem('token', 'rotated')
    vi.advanceTimersByTime(1000)
    expect(new URL(latest().url).searchParams.get('token')).toBe('rotated')
    latest().closed()
    localStorage.removeItem('token')
    vi.advanceTimersByTime(120000)
    expect(SocketStub.instances).toHaveLength(2)
  })
})

describe('connection lifecycle', () => {
  it('uses 1, 2, 4, 8, 16, 30 second retry delays and resets after open', () => {
    client.connect()
    for (const delay of [1000, 2000, 4000, 8000, 16000, 30000, 30000]) {
      const count = SocketStub.instances.length
      latest().closed()
      vi.advanceTimersByTime(delay - 1)
      expect(SocketStub.instances).toHaveLength(count)
      vi.advanceTimersByTime(1)
      expect(SocketStub.instances).toHaveLength(count + 1)
    }
    latest().open()
    latest().closed()
    const count = SocketStub.instances.length
    vi.advanceTimersByTime(1000)
    expect(SocketStub.instances).toHaveLength(count + 1)
  })

  it('disconnect cancels heartbeat and retry, and late close never restarts it', () => {
    client.connect()
    const old = latest()
    old.open()
    vi.advanceTimersByTime(30000)
    client.disconnect()
    old.closed()
    vi.advanceTimersByTime(120000)
    expect(SocketStub.instances).toHaveLength(1)
    expect(old.send).toHaveBeenCalledTimes(1)
    expect(vi.getTimerCount()).toBe(0)
  })

  it('isolates every old socket callback from the current connection', () => {
    const handler = vi.fn()
    client.onWsMessage('ANDON', handler)
    client.connect()
    const old = latest()
    old.open()
    client.disconnect()
    client.connect()
    const current = latest()
    current.open()
    old.closed()
    old.onerror?.()
    old.open()
    old.message(JSON.stringify(notification))
    client.connect()
    expect(SocketStub.instances).toHaveLength(2)
    expect(current.close).not.toHaveBeenCalled()
    expect(handler).not.toHaveBeenCalled()
    vi.advanceTimersByTime(30000)
    expect(current.send).toHaveBeenCalledExactlyOnceWith('ping')
  })

  it('deduplicates connect calls and error/close retry callbacks', () => {
    client.connect()
    client.connect()
    expect(SocketStub.instances).toHaveLength(1)
    const first = latest()
    first.onerror?.()
    first.closed()
    vi.advanceTimersByTime(1000)
    expect(SocketStub.instances).toHaveLength(2)
  })

  it('retries a constructor failure without leaking a competing retry', () => {
    const constructor = vi.fn(function (url: string) { return new SocketStub(url) })
    constructor.mockImplementationOnce(() => { throw new Error('connection failed') })
    vi.stubGlobal('WebSocket', Object.assign(constructor, { OPEN: 1, CONNECTING: 0 }))
    client.connect()
    expect(SocketStub.instances).toHaveLength(0)
    vi.advanceTimersByTime(1000)
    expect(SocketStub.instances).toHaveLength(1)
    expect(vi.getTimerCount()).toBe(0)
  })

  it('notifies connection subscribers after open and reconnect with independent cleanup', () => {
    const load = vi.fn()
    const off = client.onWsConnected(load)
    client.onWsConnected(() => { throw new Error('subscriber failed') })
    client.connect()
    latest().open()
    expect(load).toHaveBeenCalledTimes(1)
    latest().closed()
    vi.advanceTimersByTime(1000)
    latest().open()
    expect(load).toHaveBeenCalledTimes(2)
    off()
    latest().closed()
    vi.advanceTimersByTime(1000)
    latest().open()
    expect(load).toHaveBeenCalledTimes(2)
  })
})

describe('heartbeat and notifications', () => {
  it('pings after 30 seconds and reconnects 10 seconds after a missing pong', () => {
    client.connect()
    const socket = latest()
    socket.open()
    vi.advanceTimersByTime(29999)
    expect(socket.send).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    expect(socket.send).toHaveBeenCalledWith('ping')
    vi.advanceTimersByTime(9999)
    expect(socket.close).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    expect(socket.close).toHaveBeenCalledTimes(1)
    vi.advanceTimersByTime(1000)
    expect(SocketStub.instances).toHaveLength(2)
  })

  it('accepts plain pong and keeps monitoring subsequent heartbeats', () => {
    client.connect()
    latest().open()
    vi.advanceTimersByTime(30000)
    latest().message('pong')
    vi.advanceTimersByTime(30000)
    expect(latest().send).toHaveBeenCalledTimes(2)
    latest().message('pong')
    vi.advanceTimersByTime(10000)
    expect(latest().close).not.toHaveBeenCalled()
  })

  it('retires the connection and retries when sending a heartbeat fails', () => {
    client.connect()
    latest().open()
    latest().send.mockImplementationOnce(() => { throw new Error('transport failed') })
    vi.advanceTimersByTime(30000)
    expect(latest().close).toHaveBeenCalledTimes(1)
    vi.advanceTimersByTime(1000)
    expect(SocketStub.instances).toHaveLength(2)
  })

  it('an old pong cannot cancel the new connection heartbeat deadline', () => {
    client.connect()
    const old = latest()
    old.open()
    client.disconnect()
    client.connect()
    latest().open()
    vi.advanceTimersByTime(30000)
    old.message('pong')
    vi.advanceTimersByTime(10000)
    expect(latest().close).toHaveBeenCalledTimes(1)
  })

  it('cleans up an active transport when its local token disappears', () => {
    client.connect()
    latest().open()
    localStorage.removeItem('token')
    vi.advanceTimersByTime(120000)
    expect(latest().close).toHaveBeenCalledTimes(1)
    expect(latest().send).not.toHaveBeenCalled()
    expect(vi.getTimerCount()).toBe(0)
  })

  it('isolates asynchronously rejected subscribers', async () => {
    const healthy = vi.fn()
    client.onWsMessage('ANDON', () => Promise.reject(new Error('subscriber failed')))
    client.onWsMessage('ANDON', healthy)
    client.connect()
    latest().message(JSON.stringify(notification))
    await Promise.resolve()
    expect(healthy).toHaveBeenCalledWith(notification)
  })

  it('ignores malformed and unknown messages and isolates failing subscribers', () => {
    const broken = vi.fn(() => { throw new Error('subscriber failed') })
    const healthy = vi.fn()
    const off = client.onWsMessage('ANDON', broken)
    client.onWsMessage('ANDON', healthy)
    client.connect()
    latest().open()
    for (const value of [null, [], {}, 'pong', { ...notification, bizType: 'UNKNOWN' },
      { ...notification, eventType: 1 }, { ...notification, timestamp: [] },
      { ...notification, data: null }, { ...notification, data: [] }]) {
      latest().message(JSON.stringify(value))
    }
    latest().message('not json')
    expect(healthy).not.toHaveBeenCalled()
    latest().message(JSON.stringify(notification))
    expect(healthy).toHaveBeenCalledWith(notification)
    off()
    latest().message(JSON.stringify(notification))
    expect(broken).toHaveBeenCalledTimes(1)
    expect(healthy).toHaveBeenCalledTimes(2)
  })
})
