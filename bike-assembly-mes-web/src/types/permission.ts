/**
 * 菜单树节点 — 对应后端 SysMenuVO。
 * menuType: D=目录, C=菜单页, F=按钮
 */
export interface MenuTreeNode {
  menuId: number
  parentId: number
  menuName: string
  path: string | null
  component: string | null
  icon: string | null
  visible: string    // '0'=隐藏 '1'=显示
  keepAlive: string  // '0'=否 '1'=是
  redirect: string | null
  perms: string | null
  menuType: string
  orderNum: number
  status: string
  children: MenuTreeNode[]
}

/** 后端 /admin/user/menus 响应 data 部分 */
export interface UserMenusData {
  menus: MenuTreeNode[]
  permissions: string[]
}
