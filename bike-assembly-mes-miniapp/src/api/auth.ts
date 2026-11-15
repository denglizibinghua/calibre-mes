import http from '@/utils/request'
import type { LoginRequest, LoginResponse, WxLoginRequest, WxLoginResponse, WxBindRequest, WxBindResponse, UserProfileVO } from '@/types/api'

/**
 * 用户认证 API。
 *
 * 微信小程序登录：POST /app/user/wx-login
 * 用户名密码登录：POST /admin/user/login
 * 账号绑定：      POST /app/user/wx-bind
 * 获取用户信息：  GET  /app/user/me
 */

/**
 * 微信小程序登录。
 * POST /app/user/wx-login
 */
export function wxLoginApi(data: WxLoginRequest): Promise<WxLoginResponse> {
  return http.post<WxLoginResponse>('/app/user/wx-login', data as unknown as Record<string, unknown>, {
    showLoading: true,
    loadingText: '微信登录中...',
  })
}

/**
 * 验证并绑定已有系统账号；使用独立的临时凭据。
 * POST /app/user/wx-bind
 */
export function wxBindApi(data: WxBindRequest, tempToken: string): Promise<WxBindResponse> {
  return http.post<WxBindResponse>('/app/user/wx-bind', data as unknown as Record<string, unknown>, {
    header: { token: tempToken },
    showLoading: true,
    loadingText: '绑定中...',
  })
}

/**
 * 用户名密码登录（Web 管理端）。
 * POST /admin/user/login
 */
export function loginApi(data: LoginRequest): Promise<LoginResponse> {
  return http.post<LoginResponse>('/admin/user/login', data as unknown as Record<string, unknown>, {
    showLoading: true,
    loadingText: '登录中...',
  })
}

/**
 * App 端（小程序）用户名密码登录。
 * POST /app/user/login — 签发 App 端 Token，与 admin 端使用不同签名密钥。
 */
export function appLoginApi(data: LoginRequest): Promise<LoginResponse> {
  return http.post<LoginResponse>('/app/user/login', data as unknown as Record<string, unknown>, {
    showLoading: true,
    loadingText: '登录中...',
  })
}

export function faceLoginApi(data: { username?: string; imageBase64: string }): Promise<LoginResponse> {
  return http.post<LoginResponse>('/app/user/face-login', data, {
    showLoading: true,
    loadingText: '人脸识别中...',
  })
}

export function registerFaceTemplateApi(data: { imageBase64: string; consent: boolean }): Promise<void> {
  return http.post<void>('/app/user/face-template', data, {
    showLoading: true,
    loadingText: '注册人脸模板...',
  })
}

export function revokeFaceTemplateApi(): Promise<void> {
  return http.delete<void>('/app/user/face-template', { showLoading: true, loadingText: '撤销人脸模板...' })
}

export function getFaceTemplateStatusApi(): Promise<boolean> {
  return http.get<boolean>('/app/user/face-template', { showLoading: false })
}

/**
 * 获取当前登录用户档案信息（权限、角色等）。
 * GET /app/user/me
 * @param tokenOverride 可选，直接传入 token 绕过 storage 读取（用于登录后立即刷新权限的场景）
 */
export function getUserProfile(tokenOverride?: string): Promise<UserProfileVO> {
  const options = tokenOverride ? { header: { 'token': tokenOverride } } : {}
  return http.get<UserProfileVO>('/app/user/me', options)
}

/**
 * App 安全退出（撤销服务端 Refresh Token 会话）。
 * POST /app/user/logout
 */
export function appLogoutApi(): Promise<void> {
  return http.post<void>('/app/user/logout', undefined, { showLoading: false })
}
