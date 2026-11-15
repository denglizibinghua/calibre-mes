/**
 * 应用常量定义
 */

/** 本地存储 key */
export const STORAGE_KEYS = {
  TOKEN: 'token',
  ACCESS_TOKEN: 'accessToken',
  REFRESH_TOKEN: 'refreshToken',
  USER_INFO: 'userInfo',
  ACCESS_TOKEN_EXPIRES_AT: 'accessTokenExpiresAt',
  REFRESH_TOKEN_EXPIRES_AT: 'refreshTokenExpiresAt',
} as const

/** API 基础地址 - 从 Vite 环境变量读取，开发环境默认值同时定义在 .env.development */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'

/** 后端成功状态码 */
export const SUCCESS_CODE = 1
