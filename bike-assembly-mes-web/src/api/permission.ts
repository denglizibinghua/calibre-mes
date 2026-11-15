import request from './request'
import type { UserMenusData } from '@/types/permission'

const BASE = '/admin/user'

/**
 * 获取当前登录用户的菜单树和权限码。
 * 用于管理端动态路由注册和侧边栏渲染。
 */
export function getUserMenus() {
  return request.get<UserMenusData>(`${BASE}/menus`)
}
