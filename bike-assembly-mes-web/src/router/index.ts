import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import Layout from '@/layout/index.vue'
import { usePermissionStore } from '@/stores/permission'
import { useUserStore } from '@/stores/user'

// ==================== 静态基础路由 ====================

const staticRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', noAuth: true }
  },
  {
    path: '/change-password',
    name: 'ChangePassword',
    component: () => import('@/views/system/changePassword.vue'),
    meta: { title: '修改密码', noAuth: false, skipSecurityCheck: true }
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue').catch(() => ({
      render() { /* fallback */ },
      template: '<div style="text-align:center;padding:100px 0"><h1>403</h1><p>没有权限访问此页面</p></div>'
    })),
    meta: { title: '403', noAuth: true }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue').catch(() => ({
      render() { /* fallback */ },
      template: '<div style="text-align:center;padding:100px 0"><h1>404</h1><p>页面不存在</p></div>'
    })),
    meta: { title: '404', noAuth: true }
  },
  {
    path: '/',
    component: Layout,
    name: 'Layout',
    children: [
      {
        path: 'account/security',
        name: 'AccountSecurity',
        component: () => import('@/views/account/security.vue'),
        meta: { title: '账号安全' }
      }
    ]
  },
  // 全局兜底
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFoundFallback',
    // Do not redirect before the guard loads authenticated dynamic menu routes.
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes: staticRoutes
})

// ==================== 路由守卫 ====================

const WHITE_LIST = ['/login', '/403', '/404']
// 安全相关：即使未加载动态路由也可访问的页面
const SECURITY_PAGES = ['/change-password']

function getSecurityState(): { mustChangePassword: boolean; passwordExpired: boolean } {
  try {
    const raw = localStorage.getItem('userInfo')
    if (raw) {
      const info = JSON.parse(raw)
      return {
        mustChangePassword: info.mustChangePassword === 1 || info.mustChangePassword === true,
        passwordExpired: info.passwordExpired === true,
      }
    }
  } catch {
    // ignore
  }
  return { mustChangePassword: false, passwordExpired: false }
}

router.beforeEach(async (to) => {
  const token = localStorage.getItem('token')

  // ===== 1. 访问登录页 =====
  if (to.path === '/login') {
    if (token) {
      return { path: '/' }
    }
    return true
  }

  // ===== 2. 白名单页面放行 =====
  if (WHITE_LIST.some((p) => to.path.startsWith(p))) {
    return true
  }

  // ===== 3. 无 Token → 跳转登录 =====
  if (!token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // ===== 4. 安全检查：强制改密/密码过期 → 只能访问安全页面 =====
  const security = getSecurityState()
  const needsSecurityRedirect = (security.mustChangePassword || security.passwordExpired)
    && !SECURITY_PAGES.includes(to.path)

  if (needsSecurityRedirect) {
    console.log('[Router Guard] 需强制改密，重定向到修改密码页')
    return { path: '/change-password', query: { forced: '1' }, replace: true }
  }

  // ===== 5. 安全页面放行（即使是强制改密场景） =====
  if (SECURITY_PAGES.includes(to.path)) {
    return true
  }

  // ===== 6. 有 Token 但动态路由未加载 =====
  const permissionStore = usePermissionStore()
  const userStore = useUserStore()

  if (!permissionStore.routesLoaded) {
    try {
      console.log('[Router Guard] 开始加载动态菜单...')
      await permissionStore.loadPermissions()
      console.log('[Router Guard] 菜单数据条数:', permissionStore.menuTree.length)

      permissionStore.generateRoutes(router.addRoute.bind(router))
      console.log('[Router Guard] 动态路由注册完成, 总路由数:', router.getRoutes().length)

      // 如果用户没有任何菜单权限 → 403
      if (permissionStore.menuTree.length === 0) {
        console.warn('[Router Guard] 当前用户无菜单权限，跳转 403')
        return { path: '/403', replace: true }
      }

      // 根路径 → 跳转到首个可访问页面
      if (to.path === '/') {
        const firstRoute = permissionStore.getFirstAccessibleRoute()
        console.log('[Router Guard] 首个可访问页面:', firstRoute)
        if (firstRoute) {
          return { path: firstRoute, replace: true }
        }
        return { path: '/403', replace: true }
      }

      // 非根路径 → 重新进入
      console.log('[Router Guard] 重新进入:', to.path)
      return { path: to.path, query: to.query, replace: true }
    } catch (error: any) {
      console.error('[Router Guard] 加载失败:', error?.response?.status, error?.message)
      userStore.logout()
      permissionStore.resetPermission()

      if (error?.response?.status === 401 || error?.code === 'ERR_NETWORK') {
        return { path: '/login', query: { redirect: to.fullPath }, replace: true }
      }
      // 通用失败：回登录页
      return { path: '/login', replace: true }
    }
  }

  // ===== 7. 路由已加载 → 正常放行 =====
  if (to.path === '/') {
    const firstRoute = permissionStore.getFirstAccessibleRoute()
    console.log('[Router Guard] 已登录, 根路径→', firstRoute)
    if (firstRoute) {
      return { path: firstRoute, replace: true }
    }
    return { path: '/403', replace: true }
  }

  return true
})

export default router
