import axios, { type AxiosResponse, type InternalAxiosRequestConfig, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResult } from '@/types/api'
import { idempotencyKey } from '@/utils/idempotencyKey'
import { disconnect } from '@/utils/useWebSocket'
import { repairTextFields } from '@/utils/text'

/** 幂等键扩展：写请求附加的本地 actionKey（用于成功后清除） */
interface IdemRequestConfig extends InternalAxiosRequestConfig {
  _idemActionKey?: string
}

const API_BASE_URL = import.meta.env.DEV
  ? ''
  : (import.meta.env.VITE_API_BASE_URL ?? '')

const service = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

// ==================== Token 刷新队列 ====================
// [Token] 并发请求时的刷新队列机制：
//   当多个请求同时遇到 401 时，只有第一个请求执行实际的 Token 刷新，
//   其余请求加入 pendingRequests 队列等待，刷新完成后共享新的 Token。
//   避免多个请求同时刷新导致 Token 竞争和多次刷新。

let isRefreshing = false
let pendingRequests: Array<{
  resolve: (token: string) => void
  reject: (err: any) => void
}> = []

// [Token] 从 localStorage 读取 Access Token（key: "token"）
function getAccessToken(): string | null {
  return localStorage.getItem('token')
}
// [Token] 从 localStorage 读取 Refresh Token（key: "refreshToken"）
function getRefreshToken(): string | null {
  return localStorage.getItem('refreshToken')
}
// [Token] 刷新后同时更新两个 Token
function saveTokens(accessToken: string, refreshToken: string) {
  localStorage.setItem('token', accessToken)
  localStorage.setItem('refreshToken', refreshToken)
}
// [Token] 清除所有本地存储的认证信息
function clearTokens() {
  disconnect()
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('userInfo')
}

// [Token] 实际执行 Token 刷新：用 Refresh Token 向 /admin/user/refresh-token 换取新的 Token 对
async function doRefreshToken(): Promise<string> {
  const rt = getRefreshToken()
  if (!rt) throw new Error('No refresh token')

  // [前端调用] POST /admin/user/refresh-token，Refresh Token 放在请求头 token 中
  const response = await axios.post(`${API_BASE_URL}/admin/user/refresh-token`, null, {
    headers: { 'token': rt, 'Content-Type': 'application/json' },
    timeout: 10000
  })

  const data = response.data
  if (data?.code === 1 && data?.data) {
    const { accessToken, refreshToken } = data.data
    saveTokens(accessToken, refreshToken)
    return accessToken
  }
  throw new Error(data?.msg || 'Refresh failed')
}

function addPendingRequest(): Promise<string> {
  return new Promise((resolve, reject) => {
    pendingRequests.push({ resolve, reject })
  })
}

function resolvePendingRequests(token: string) {
  pendingRequests.forEach(p => p.resolve(token))
  pendingRequests = []
}

function rejectPendingRequests(err: any) {
  pendingRequests.forEach(p => p.reject(err))
  pendingRequests = []
}

// ==================== 请求拦截器 ====================

service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // [Token] 刷新接口不自动携带 Access Token（它需要的是 Refresh Token，由 doRefreshToken 手动设置）
    if (config.url?.includes('/refresh-token')) {
      return config
    }
    // [Token] 每次请求自动从 localStorage 取出 Access Token，放在请求头 token 中
    const token = getAccessToken()
    if (token) {
      config.headers['token'] = token
    }
    // [幂等] 写请求自动附加 Idempotency-Key，与后端 @IdempotencyRequired 对齐；
    // 同一方法+URL 在操作成功/业务失败前复用同一把键，超时重试不重复执行
    const method = (config.method || 'get').toUpperCase()
    if (method === 'POST' || method === 'PUT' || method === 'DELETE') {
      const actionKey = `web:${method}:${config.url || ''}`
      config.headers['Idempotency-Key'] = idempotencyKey.getOrCreate(actionKey)
      ;(config as IdemRequestConfig)._idemActionKey = actionKey
    }
    return config
  },
  (error) => Promise.reject(error)
)

/** 操作成功后清除幂等键；业务失败（除"处理中"）也清除以便修改参数后重试 */
function clearIdemKey(config?: IdemRequestConfig, errMsg?: string) {
  const actionKey = config?._idemActionKey
  if (!actionKey) return
  if (errMsg && errMsg.includes('处理中')) return
  idempotencyKey.clear(actionKey)
}

// ==================== 响应拦截器（含自动刷新） ====================

service.interceptors.response.use(
  (response: AxiosResponse<ApiResult>): any => {
    if (response.config.responseType === 'blob') {
      return response
    }
    const res = response.data
    if (res.code === 1) {
      clearIdemKey(response.config)
      return repairTextFields(res.data)
    }
    const errMsg = res.msg || '请求失败'
    clearIdemKey(response.config, errMsg)
    ElMessage.error(errMsg)
    return Promise.reject(new Error(errMsg))
  },
  async (error) => {
    if (!error.response) {
      ElMessage.error('网络异常，请检查连接')
      return Promise.reject(error)
    }

    const status = error.response.status
    const resData = error.response.data
    const serverMsg = resData?.message || resData?.msg || ''
    const originalRequest = error.config

    // [Token] 401 — Access Token 过期，自动使用 Refresh Token 刷新
    if (status === 401 && originalRequest && !originalRequest._retry) {
      // 排除刷新接口自身，防止无限递归
      if (originalRequest.url?.includes('/refresh-token')) {
        clearTokens()
        if (window.location.pathname !== '/login') {
          window.location.href = '/login'
        }
        return Promise.reject(error)
      }

      if (isRefreshing) {
        // [Token] 已有刷新在进行中，当前请求加入等待队列，刷新完成后共享新 Token 并重试
        return addPendingRequest().then(token => {
          originalRequest.headers['token'] = token
          return service(originalRequest)
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        // [Token] 执行 Token 刷新 → 成功后通知队列中所有等待的请求
        const newToken = await doRefreshToken()
        resolvePendingRequests(newToken)
        originalRequest.headers['token'] = newToken
        return service(originalRequest)  // 用新 Token 重试原请求
      } catch (refreshErr) {
        // [Token] 刷新失败（Refresh Token 也过期/无效）→ 清除本地状态，跳转登录页
        rejectPendingRequests(refreshErr)
        clearTokens()
        ElMessage.error('登录已失效，请重新登录')
        if (window.location.pathname !== '/login') {
          window.location.href = '/login'
        }
        return Promise.reject(refreshErr)
      } finally {
        isRefreshing = false
      }
    }

    // [异常处理] 其他状态码：根据 HTTP 状态码显示不同的错误提示
    switch (status) {
      case 401:
        clearTokens()
        ElMessage.error('登录已失效，请重新登录')
        if (window.location.pathname !== '/login') window.location.href = '/login'
        break
      case 403: {
        // [业务规则] 403 有两种情况：
        //   1. 密码安全状态 → 结构化错误码 SECURITY_PASSWORD_CHANGE_REQUIRED，前端自动跳转修改密码页
        //   2. 普通权限不足 → 显示"没有权限执行此操作"
        const errorCode = (resData?.data && resData.data.errorCode) ? resData.data.errorCode : null
        if (errorCode === 'SECURITY_PASSWORD_CHANGE_REQUIRED') {
          ElMessage.warning('请先修改密码')
          try {
            const raw = localStorage.getItem('userInfo')
            if (raw) {
              const info = JSON.parse(raw)
              info.mustChangePassword = 1
              localStorage.setItem('userInfo', JSON.stringify(info))
            }
          } catch { /* ignore */ }
          if (window.location.pathname !== '/change-password') {
            window.location.href = '/change-password?forced=1'
          }
        } else {
          ElMessage.error(serverMsg || '没有权限执行此操作')
        }
        break
      }
      case 400:
        ElMessage.warning(serverMsg || '请求参数有误')
        break
      case 404:
        ElMessage.error(serverMsg || '请求的资源不存在')
        break
      case 500:
        ElMessage.error(serverMsg || '服务器内部错误')
        break
      default:
        ElMessage.error(serverMsg || error.message || '请求失败')
    }
    return Promise.reject(error)
  }
)

const request = {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.get(url, config) as Promise<T>
  },
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return service.post(url, data, config) as Promise<T>
  },
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return service.put(url, data, config) as Promise<T>
  },
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.delete(url, config) as Promise<T>
  },
  exportBlob(url: string, params?: Record<string, unknown>): Promise<AxiosResponse> {
    return service.get(url, { params, responseType: 'blob' })
  }
}

export default request
