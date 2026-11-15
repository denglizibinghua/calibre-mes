/**
 * 后端统一返回结构 Result<T>
 *
 * code:
 *   1  - 成功
 *   0  - 业务错误
 *   400 - 参数校验错误
 *   401 - 未登录/登录失效
 *   403 - 无权限
 *   500 - 服务器未知异常
 */
export interface ApiResult<T = unknown> {
  code: number
  msg: string
  data: T
  path?: string
  timestamp?: string
  requestId?: string
}

// ==================== 登录 ====================

/** 密码登录请求 - 对应后端 UserLoginDTO */
export interface LoginRequest {
  username: string
  password: string
}

/** 微信登录请求 - 对应后端 WxLoginDTO */
export interface WxLoginRequest {
  code: string
  nickname?: string
  avatar?: string
}

/** 登录响应 - 对应后端 UserLoginVO */
export interface LoginResponse {
  id: number
  username: string
  nickname: string
  roleId: number
  /** @deprecated 兼容字段，等于 accessToken */
  token: string
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  refreshExpiresIn: number
  mustChangePassword?: number
  passwordExpired?: boolean
  permissions?: string[]
  dataScope?: string
}

/** 当前用户信息（存储用） */
export interface UserInfo {
  id: number
  username: string
  nickname: string
  roleId: number
  token: string
  accessToken?: string
  refreshToken?: string
  expiresIn?: number
  refreshExpiresIn?: number
  permissions?: string[]
  dataScope?: string
  mustChangePassword?: number
  passwordExpired?: boolean
}

/** 用户档案 VO - 对应后端 UserProfileVO / /app/user/me 返回 */
export interface UserProfileVO {
  userId: number
  username: string
  nickname: string
  roles: string[]
  permissions: string[]
  dataScope: string
  mustChangePassword?: number
  passwordExpired?: boolean
}

// ==================== 微信登录 ====================

/** 微信登录响应 - 对应后端 WxLoginVO
 *  status: 1=完整登录 2=旧协议保留 3=需绑定已有系统账号 */
export interface WxLoginResponse {
  status: number
  // status=1 (完整登录)
  userId?: number
  username?: string
  nickname?: string
  roleId?: number
  /** @deprecated 兼容字段，等于 accessToken */
  token?: string
  accessToken?: string
  refreshToken?: string
  tokenType?: string
  expiresIn?: number
  refreshExpiresIn?: number
  needBind?: boolean
  mustChangePassword?: number
  passwordExpired?: boolean
  userInfo?: {
    userId: number
    username: string
    nickname: string
    roleId: number
    permissions: string[]
    dataScope: string
  }
  // status=2,3 (需绑定/角色)
  tempToken?: string
  // 所有状态都返回
  availableRoles?: RoleOption[]
  // RBAC 权限
  permissions?: string[]
  dataScope?: string
}

/** 角色选项 */
export interface RoleOption {
  roleId: number
  roleName: string
}

/** 微信绑定请求 - 对应后端 WxBindDTO */
export interface WxBindRequest {
  bindType: 'existing'
  username: string
  password: string
}

/** 微信绑定响应 - 对应后端 WxBindVO */
export interface WxBindResponse {
  userId: number
  username: string
  nickname: string
  roleId: number
  /** @deprecated 兼容字段，等于 accessToken */
  token: string
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  refreshExpiresIn: number
  mustChangePassword?: number
  passwordExpired?: boolean
  permissions?: string[]
  dataScope?: string
}

// ==================== 分页 ====================

/** 分页查询参数 */
export interface PageQuery {
  page?: number
  size?: number
}

/** 分页结果 - 对应后端 PageResult<T> */
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}
