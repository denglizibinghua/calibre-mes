import { API_BASE_URL, STORAGE_KEYS, SUCCESS_CODE } from '@/constants'
import type { ApiResult } from '@/types/api'
import { cache, type CacheOptions } from './cache'
import { idempotencyKey } from './idempotencyKey'
import { closeWs } from './websocket'

/** 缓存策略 */
export type CachePolicy = 'network-first' | 'cache-first' | 'stale-while-revalidate' | 'no-cache'

/** 请求配置选项 */
export interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: Record<string, unknown>
  header?: Record<string, string>
  showLoading?: boolean
  loadingText?: string
  /** 缓存策略（仅 GET 请求生效），默认 no-cache */
  cachePolicy?: CachePolicy
  /** 缓存配置（cachePolicy 非 no-cache 时启用） */
  cacheOptions?: Omit<CacheOptions, 'userIsolation'>
}

/** 是否正在刷新 token */
let isRefreshing = false
/** 等待刷新的请求队列 */
let refreshQueue: Array<{ resolve: (token: string) => void; reject: (err: Error) => void }> = []

/** 是否正在执行登录页跳转（防止重复跳转） */
let isRedirecting = false

/** 上次权限刷新时间（避免频繁重复请求） */
let lastPermissionRefresh = 0
const PERMISSION_REFRESH_INTERVAL = 30_000 // 30 秒内不重复刷新

/** HTTP 请求方法 */
type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE'

/**
 * 从本地存储读取 token。
 * 使用 uni.getStorageSync 而非 localStorage。
 */
function getToken(): string {
  try {
    return uni.getStorageSync(STORAGE_KEYS.ACCESS_TOKEN) || uni.getStorageSync(STORAGE_KEYS.TOKEN) || ''
  } catch {
    return ''
  }
}

function getRefreshToken(): string {
  try {
    return uni.getStorageSync(STORAGE_KEYS.REFRESH_TOKEN) || ''
  } catch {
    return ''
  }
}

function saveTokens(accessToken: string, refreshToken: string): void {
  try {
    uni.setStorageSync(STORAGE_KEYS.ACCESS_TOKEN, accessToken)
    uni.setStorageSync(STORAGE_KEYS.TOKEN, accessToken)
    uni.setStorageSync(STORAGE_KEYS.REFRESH_TOKEN, refreshToken)
  } catch {
    // 存储写入失败不影响内存状态
  }
}

function clearAllTokens(): void {
  closeWs()
  try {
    uni.removeStorageSync(STORAGE_KEYS.ACCESS_TOKEN)
    uni.removeStorageSync(STORAGE_KEYS.TOKEN)
    uni.removeStorageSync(STORAGE_KEYS.REFRESH_TOKEN)
    uni.removeStorageSync(STORAGE_KEYS.ACCESS_TOKEN_EXPIRES_AT)
    uni.removeStorageSync(STORAGE_KEYS.REFRESH_TOKEN_EXPIRES_AT)
    uni.removeStorageSync(STORAGE_KEYS.USER_INFO)
  } catch {
    // 存储清理失败不影响内存状态
  }
}

/** 调用刷新接口获取新 token */
async function tryRefreshToken(clientType: string): Promise<string> {
  const rt = getRefreshToken()
  if (!rt) throw new Error('No refresh token')

  return new Promise((resolve, reject) => {
    uni.request({
      url: API_BASE_URL + (clientType === 'admin' ? '/admin/user/refresh-token' : '/app/user/refresh-token'),
      method: 'POST',
      header: { 'Content-Type': 'application/json', 'token': rt },
      success: (res) => {
        const body = res.data as ApiResult<{ accessToken: string; refreshToken: string }>
        if (body && body.code === 1 && body.data) {
          saveTokens(body.data.accessToken, body.data.refreshToken)
          resolve(body.data.accessToken)
        } else {
          reject(new Error(body?.msg || 'Refresh failed'))
        }
      },
      fail: (err) => reject(new Error(err.errMsg || 'Network error')),
    })
  })
}

/**
 * 清除登录状态并跳转登录页。
 * 内置防重复跳转保护。尝试调用服务端退出接口。
 */
function handleAuthExpired(): void {
  // 尝试调用服务端退出接口（fire-and-forget）
  const rt = getRefreshToken()
  if (rt) {
    uni.request({
      url: API_BASE_URL + '/app/user/logout',
      method: 'POST',
      header: { 'Content-Type': 'application/json', 'token': rt },
      complete: () => { /* 不关心结果 */ },
    })
  }

  clearAllTokens()

  if (isRedirecting) return
  isRedirecting = true

  uni.showToast({ title: '登录已失效，请重新登录', icon: 'none', duration: 2000 })

  // 延迟跳转，给 toast 展示时间
  setTimeout(() => {
    uni.reLaunch({ url: '/pages/login/index' })
    isRedirecting = false
  }, 2000)
}

/**
 * 处理 403 无权限响应。
 * 先尝试刷新权限，如果刷新后仍无权限，则不重放危险写操作。
 * 只对 GET 请求重试，POST/PUT/DELETE 不重放。
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function handleForbidden(
  url: string,
  method: string,
  data: Record<string, unknown> | undefined,
  options: RequestOptions,
  resolve: (value: any) => void,
  reject: (reason: Error) => void,
  serverMsg?: string,
  responseData?: any,
): Promise<void> {
  // 动态导入避免 request -> store -> api/auth 静态循环依赖
  const { useUserStore } = await import('@/stores/user')
  const userStore = useUserStore()

  const now = Date.now()
  if (now - lastPermissionRefresh > PERMISSION_REFRESH_INTERVAL) {
    lastPermissionRefresh = now
    try {
      await userStore.refreshProfile()
    } catch {
      // 刷新失败继续
    }
  }

  // 安全状态：检查结构化 errorCode
  const errorCode = responseData?.data?.errorCode || ''
  if (errorCode === 'SECURITY_PASSWORD_CHANGE_REQUIRED') {
    if (userStore.userInfo) {
      userStore.userInfo = { ...userStore.userInfo, mustChangePassword: 1 }
    }
    uni.showToast({ title: '需要修改密码，请使用管理端', icon: 'none', duration: 2000 })
    setTimeout(() => {
      uni.reLaunch({ url: '/pages/security-block/index' })
    }, 1500)
    reject(new Error('需要修改密码'))
    return
  }

  // 账号已禁用
  if (serverMsg && (serverMsg.includes('禁用') || serverMsg.includes('disabled'))) {
    userStore.logout()
    uni.showToast({ title: '账号已被禁用', icon: 'none', duration: 2000 })
    handleAuthExpired()
    reject(new Error('账号已被禁用'))
    return
  }

  uni.showToast({ title: serverMsg || '无权限访问', icon: 'none', duration: 2000 })
  reject(new Error(serverMsg || '无权限访问'))
}

/**
 * Token 刷新处理 — 自动刷新 + 请求队列。
 */
async function handleTokenRefresh<TRes>(
  url: string,
  method: string,
  data: Record<string, unknown> | undefined,
  options: RequestOptions,
  resolve: (value: any) => void,
  reject: (reason: Error) => void,
): Promise<void> {
  if (isRefreshing) {
    // 已有刷新进行中，排队等待
    refreshQueue.push({
      resolve: (token: string) => {
        // 重放原请求
        request<TRes>({ ...options, url, method: method as any, data })
          .then(resolve).catch(reject)
      },
      reject,
    })
    return
  }

  isRefreshing = true
  try {
    const clientType = url.startsWith('/admin') ? 'admin' : 'app'
    const newToken = await tryRefreshToken(clientType)

    // 重放排队请求
    refreshQueue.forEach(q => q.resolve(newToken))
    refreshQueue = []

    // 重放当前请求
    request<TRes>({ ...options, url, method: method as any, data })
      .then(resolve).catch(reject)
  } catch {
    refreshQueue.forEach(q => q.reject(new Error('Token 刷新失败')))
    refreshQueue = []
    clearAllTokens()
    handleAuthExpired()
    reject(new Error('登录已失效'))
  } finally {
    isRefreshing = false
  }
}

/**
 * 发起 uni.request 请求，自动解析后端统一返回结构 Result<T>。
 *
 * - code === 1 时返回 data 字段
 * - code 为其他业务错误码时 reject 并展示 msg
 * - HTTP 401 或 code === 401 时清除登录状态并跳转登录页
 * - 网络失败时 reject 并展示统一提示
 * - loading 在成功或失败时都能关闭
 *
 * @typeParam T - 实际业务数据类型（Result.data 的类型）
 */
function request<T = unknown>(options: RequestOptions): Promise<T> {
  const {
    url,
    method = 'GET',
    data,
    header = {},
    showLoading = true,
    loadingText = '加载中...',
    cachePolicy = 'no-cache',
    cacheOptions,
  } = options

  // 缓存策略（仅 GET 请求且非刷新接口）
  const canCache = method === 'GET' && !url.includes('/refresh-token') && cacheOptions
  const cacheKey = canCache ? url + (data ? '?' + JSON.stringify(data) : '') : ''

  // cache-first: 优先返回缓存
  if (canCache && cachePolicy === 'cache-first') {
    const cached = cache.get(cacheKey, cacheOptions as CacheOptions)
    if (cached) return Promise.resolve(cached as T)
  }

  // stale-while-revalidate: 先返回缓存，后台更新
  if (canCache && cachePolicy === 'stale-while-revalidate') {
    const cached = cache.get(cacheKey, cacheOptions as CacheOptions)
    if (cached) {
      // 后台静默更新
      executeNetworkRequest().then((fresh: T) => {
        if (fresh) cache.set(cacheKey, fresh, cacheOptions as CacheOptions)
      }).catch(() => {})
      return Promise.resolve(cached as T)
    }
  }

  // 自动从本地存储读取 token 并放入请求头
  const token = getToken()
  const finalHeader: Record<string, string> = {
    'Content-Type': 'application/json',
    ...header,
  }
  if (token && !finalHeader['token']) {
    finalHeader['token'] = token
  }

  // 写请求（POST/PUT/DELETE）自动附加 Idempotency-Key：
  // 后端幂等键字段上限 128 字符，因此只能用"方法 + URL"生成短键（不能拼接请求体）；
  // 同一操作重试复用同一把键，成功后清除；业务失败也清除以便修改参数后重试（失败事务已回滚）。
  const isWriteMethod = method === 'POST' || method === 'PUT' || method === 'DELETE'
  let idempotencyActionKey = ''
  if (isWriteMethod) {
    idempotencyActionKey = `miniapp:${method}:${url}`
    finalHeader['Idempotency-Key'] = idempotencyKey.getOrCreate(idempotencyActionKey)
  }

  if (showLoading) {
    uni.showLoading({ title: loadingText, mask: true })
  }

  /** 执行实际的网络请求（内联函数，供 stale-while-revalidate 复用） */
  function executeNetworkRequest(): Promise<T> {
    return new Promise<T>((resolve, reject) => {
      uni.request({
        url: API_BASE_URL + url,
        method: method as HttpMethod,
        data: method === 'GET' ? undefined : data,
        dataType: 'json',
        header: finalHeader,
        success: (res) => {
          if (showLoading) {
            uni.hideLoading()
          }

          const statusCode = res.statusCode
          const body = res.data as ApiResult<T>

          // HTTP 403 或业务响应 code === 403 → 无权限
          // 尝试刷新权限后重试一次
          if (statusCode === 403 || (body && body.code === 403)) {
            handleForbidden(url, method, data, options, resolve, reject, body?.msg, body)
            return
          }

          // HTTP 401 或业务响应 code === 401 → 尝试刷新或登录失效
          if (statusCode === 401 || (body && body.code === 401)) {
            // 排除刷新接口自身
            if (url.includes('/refresh-token')) {
              clearAllTokens()
              handleAuthExpired()
              reject(new Error(body?.msg || '登录已失效'))
              return
            }
            // 尝试用 Refresh Token 刷新
            handleTokenRefresh<T>(url, method, data, options, resolve, reject)
            return
          }

          // 请求成功，解析 Result<T>
          if (body && body.code === SUCCESS_CODE) {
            if (idempotencyActionKey) {
              idempotencyKey.clear(idempotencyActionKey)
            }
            resolve(body.data as T)
            return
          }

          // 业务错误（code !== 1）
          const errMsg = body?.msg || '请求失败'
          // 业务失败时清除幂等键（"处理中"除外，需保留同一把键防止并发重试重复执行）
          if (idempotencyActionKey && !errMsg.includes('处理中')) {
            idempotencyKey.clear(idempotencyActionKey)
          }
          uni.showToast({ title: errMsg, icon: 'none', duration: 2000 })
          reject(new Error(errMsg))
        },
        fail: (err) => {
          if (showLoading) {
            uni.hideLoading()
          }
          const errMsg = '网络异常，请检查网络连接'
          uni.showToast({ title: errMsg, icon: 'none', duration: 2000 })
          reject(new Error(errMsg))
        },
      })
    })
  }

  // 执行实际的网络请求
  const networkPromise = executeNetworkRequest()

  // 网络优先：成功后更新缓存
  if (canCache && (cachePolicy === 'network-first' || cachePolicy === 'stale-while-revalidate')) {
    return networkPromise.then((result) => {
      cache.set(cacheKey, result, cacheOptions as CacheOptions)
      return result
    })
  }

  return networkPromise
}

/** 便捷方法 */
export const http = {
  get<T = unknown>(url: string, options?: Omit<RequestOptions, 'url' | 'method'>): Promise<T> {
    return request<T>({ ...options, url, method: 'GET' })
  },
  post<T = unknown>(url: string, data?: Record<string, unknown>, options?: Omit<RequestOptions, 'url' | 'method' | 'data'>): Promise<T> {
    return request<T>({ ...options, url, method: 'POST', data })
  },
  put<T = unknown>(url: string, data?: Record<string, unknown>, options?: Omit<RequestOptions, 'url' | 'method' | 'data'>): Promise<T> {
    return request<T>({ ...options, url, method: 'PUT', data })
  },
  delete<T = unknown>(url: string, options?: Omit<RequestOptions, 'url' | 'method'>): Promise<T> {
    return request<T>({ ...options, url, method: 'DELETE' })
  },
}

export { request, handleAuthExpired }
export default http
