import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import type { MenuTreeNode } from '@/types/permission'
import { getUserMenus } from '@/api/permission'
import { repairTextEncoding } from '@/utils/text'

/**
 * 权限 Store：管理动态菜单树、权限码、动态路由。
 */
export const usePermissionStore = defineStore('permission', () => {
  // ========== 状态 ==========
  const menuTree = ref<MenuTreeNode[]>([])
  const permissions = ref<string[]>([])
  const routesLoaded = ref(false)

  /** 已注册的动态路由移除函数列表，退出时遍历清理 */
  const removeRouteFns: (() => void)[] = []

  // ========== 计算属性 ==========

  /** 侧边栏菜单（过滤隐藏菜单和按钮类型） */
  const sidebarMenus = computed(() => filterSidebarMenus(menuTree.value))

  /** 扁平权限码 Set，用于 O(1) 查找 */
  const permissionSet = computed(() => new Set(permissions.value))

  // ========== 方法 ==========

  /**
   * 从后端加载当前用户菜单和权限码。
   */
  async function loadPermissions(): Promise<void> {
    try {
      const data = await getUserMenus()
      menuTree.value = (data.menus || []).map(normalizeMenuText)
      permissions.value = data.permissions || []
      routesLoaded.value = false  // 菜单更新后需要重新注册路由
    } catch (error: any) {
      console.error('获取用户菜单失败', error)
      throw error
    }
  }

  /**
   * 将菜单树转换为 Vue Router 路由配置。
   * 递归遍历菜单树，正确构建父子路径。
   */
  function generateRoutes(
    routerAddRoute: (parentName: string, route: RouteRecordRaw) => void
  ): void {
    // 先清除旧的动态路由
    removeDynamicRoutes()
    removeRouteFns.length = 0

    // 递归注册
    registerTreeRoutes(menuTree.value, '', routerAddRoute)

    routesLoaded.value = true
  }

  /**
   * 递归遍历菜单树注册路由，传递父路径上下文。
   */
  function registerTreeRoutes(
    nodes: MenuTreeNode[],
    parentPath: string,
    routerAddRoute: (parentName: string, route: RouteRecordRaw) => void
  ): void {
    for (const menu of nodes) {
      // 构建当前节点的完整路径
      const fullPath = buildFullPath(menu, parentPath)

      if (menu.menuType === 'C') {
        // 菜单页：注册为路由
        const component = resolveComponent(menu.component)
        if (component) {
          const route: RouteRecordRaw = {
            path: fullPath,
            name: `menu_${menu.menuId}`,
            component,
            meta: {
              title: menu.menuName,
              icon: menu.icon || '',
              hidden: menu.visible === '0',
              keepAlive: menu.keepAlive === '1',
              menuId: menu.menuId,
              perms: menu.perms || undefined
            }
          }
          try {
            const removeFn = routerAddRoute('Layout', route)
            if (typeof removeFn === 'function') {
              removeRouteFns.push(removeFn)
            }
          } catch (e) {
            if (import.meta.env.DEV) {
              console.warn(`[动态路由] 注册失败: ${menu.menuName}, path=${fullPath}`, e)
            }
          }
        } else {
          if (import.meta.env.DEV) {
            console.warn(
              `[动态路由] 组件未解析: menuName="${menu.menuName}", component="${menu.component}"`
            )
          }
        }
      }

      // 递归处理子菜单
      if (menu.children && menu.children.length > 0) {
        // 子菜单的父路径：目录(D)传递自己的路径，菜单页(C)不传递（子页面同级）
        const childParentPath = menu.menuType === 'D' ? fullPath : parentPath
        registerTreeRoutes(menu.children, childParentPath, routerAddRoute)
      }
    }
  }

  /**
   * 构建菜单的完整路由路径。
   */
  function buildFullPath(menu: MenuTreeNode, parentPath: string): string {
    if (!menu.path) return ''
    // 已是以 / 开头的绝对路径
    if (menu.path.startsWith('/')) {
      return menu.path
    }
    // 相对路径：拼接父路径
    return `${parentPath}/${menu.path}`.replace(/\/+/g, '/')
  }

  /**
   * 检查是否拥有指定权限。
   */
  function hasPermission(perm: string): boolean {
    if (!perm) return true
    // 管理员拥有所有权限（通配符）
    if (permissionSet.value.has('*:*:*')) return true
    return permissionSet.value.has(perm)
  }

  /**
   * 检查是否拥有任一权限。
   */
  function hasAnyPermission(...perms: string[]): boolean {
    return perms.some((p) => hasPermission(p))
  }

  /**
   * 检查是否拥有全部权限。
   */
  function hasAllPermissions(...perms: string[]): boolean {
    return perms.every((p) => hasPermission(p))
  }

  /**
   * 获取用户首个可访问的叶子菜单路径。
   * 优先返回 /dashboard，否则返回菜单树中第一个可见叶子菜单页。
   * 用于动态计算登录后的默认跳转地址。
   */
  function getFirstAccessibleRoute(): string | null {
    return findFirstLeaf(menuTree.value, '')
  }

  function findFirstLeaf(nodes: MenuTreeNode[], parentPath: string): string | null {
    let dashboardFallback: string | null = null

    for (const node of nodes) {
      // 跳过按钮和隐藏菜单
      if (node.menuType === 'F' || node.visible === '0') continue

      // 计算完整路径
      let fullPath = node.path || ''
      if (fullPath && !fullPath.startsWith('/')) {
        fullPath = `${parentPath}/${fullPath}`.replace(/\/+/g, '/')
      }

      if (node.menuType === 'C') {
        // dashboard 优先返回
        if (fullPath === '/dashboard') return '/dashboard'
        // 记录第一个非 dashboard 叶子页
        if (!dashboardFallback && fullPath) dashboardFallback = fullPath
      }

      // 递归子菜单
      if (node.children && node.children.length > 0) {
        const childResult = findFirstLeaf(
          node.children,
          node.menuType === 'D' ? fullPath : parentPath
        )
        if (childResult) {
          if (childResult === '/dashboard') return '/dashboard'
          if (!dashboardFallback) dashboardFallback = childResult
        }
      }
    }
    return dashboardFallback
  }

  /**
   * 重置权限状态（退出登录时调用）。
   */
  function resetPermission(): void {
    removeDynamicRoutes()
    removeRouteFns.length = 0
    menuTree.value = []
    permissions.value = []
    routesLoaded.value = false
  }

  // ========== 私有辅助 ==========

  /** 修复历史菜单名称的编码乱码，正常中文原样返回。 */
  function normalizeMenuText(menu: MenuTreeNode): MenuTreeNode {
    return {
      ...menu,
      menuName: repairTextEncoding(menu.menuName),
      children: menu.children?.map(normalizeMenuText) ?? menu.children
    }
  }

  /**
   * 从路由表中移除所有动态注册的路由。
   */
  function removeDynamicRoutes(): void {
    for (const fn of removeRouteFns) {
      try { fn() } catch { /* ignore */ }
    }
  }

  /**
   * 过滤侧边栏菜单：只保留 D/C 类型且 visible='1' 的节点。
   * 递归过滤，如果父目录下所有子菜单都被隐藏，父目录也不显示。
   */
  function filterSidebarMenus(nodes: MenuTreeNode[]): MenuTreeNode[] {
    if (!nodes || nodes.length === 0) return []

    return nodes
      .filter((node) => {
        // 按钮类型不显示
        if (node.menuType === 'F') return false
        // 隐藏菜单不显示
        if (node.visible === '0') return false
        return true
      })
      .map((node) => {
        const filteredChildren = filterSidebarMenus(node.children || [])
        return { ...node, children: filteredChildren }
      })
      .filter((node) => {
        // 目录类型但没有可见子菜单的不显示
        if (node.menuType === 'D' && (!node.children || node.children.length === 0)) {
          return false
        }
        return true
      })
  }

  /**
   * 使用 import.meta.glob 安全解析组件。
   * 只允许加载 ../views/ 下的 .vue 文件。
   */
  function resolveComponent(componentPath: string | null): (() => Promise<any>) | null {
    if (!componentPath || componentPath === 'Layout') {
      return null
    }

    // 标准化组件路径
    let normalized = componentPath.trim()

    // 去掉可能的 views/ 前缀
    if (normalized.startsWith('views/')) {
      normalized = normalized.slice(6)
    }

    // 去掉可能的 .vue 后缀
    if (normalized.endsWith('.vue')) {
      normalized = normalized.slice(0, -4)
    }

    // 去掉首尾 /
    normalized = normalized.replace(/^\/+|\/+$/g, '')

    // 使用 glob 映射（Vite 构建时确定）
    const viewModules = import.meta.glob('../views/**/*.vue') as Record<string, () => Promise<any>>

    // 尝试多种路径格式匹配
    const candidates = [
      `../views/${normalized}.vue`,
      `../views/${normalized}/index.vue`
    ]

    for (const candidate of candidates) {
      if (viewModules[candidate]) {
        return viewModules[candidate]
      }
    }

    // 开发环境告警
    if (import.meta.env.DEV) {
      console.warn(`[动态路由] 组件路径未匹配: "${componentPath}" → 尝试了 [${candidates.join(', ')}]`)
      console.warn(`[动态路由] 可用组件:`, Object.keys(viewModules))
    }

    return null
  }

  return {
    // 状态
    menuTree,
    permissions,
    routesLoaded,
    // 计算属性
    sidebarMenus,
    permissionSet,
    // 方法
    loadPermissions,
    generateRoutes,
    hasPermission,
    hasAnyPermission,
    hasAllPermissions,
    getFirstAccessibleRoute,
    resetPermission
  }
})
