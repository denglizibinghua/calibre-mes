<template>
  <!-- 目录类型：有可见子菜单时渲染 el-sub-menu -->
  <el-sub-menu
    v-if="isDirectory && hasVisibleChildren"
    :index="String(menu.menuId)"
  >
    <template #title>
      <el-icon v-if="iconComponent">
        <component :is="iconComponent" />
      </el-icon>
      <span>{{ menu.menuName }}</span>
    </template>

    <SidebarMenuItem
      v-for="child in visibleChildren"
      :key="child.menuId"
      :menu="child"
      :parent-path="fullPath"
    />
  </el-sub-menu>

  <!-- 菜单页类型：渲染 el-menu-item，使用完整路径 -->
  <el-menu-item
    v-else-if="isPage"
    :index="fullPath"
  >
    <el-icon v-if="iconComponent">
      <component :is="iconComponent" />
    </el-icon>
    <span>{{ menu.menuName }}</span>
  </el-menu-item>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MenuTreeNode } from '@/types/permission'

const props = withDefaults(defineProps<{
  menu: MenuTreeNode
  parentPath?: string
}>(), {
  parentPath: ''
})

const menu = props.menu

/** 是否为目录 */
const isDirectory = computed(() => menu.menuType === 'D')

/** 是否为菜单页 */
const isPage = computed(() => menu.menuType === 'C' || menu.menuType === 'M')

/** 计算当前菜单的完整路由路径 */
const fullPath = computed(() => {
  const p = menu.path
  if (!p) return ''
  // 绝对路径直接返回
  if (p.startsWith('/')) return p
  // 相对路径拼接父路径
  return `${props.parentPath}/${p}`.replace(/\/+/g, '/')
})

/** 过滤后的可见子菜单 */
const visibleChildren = computed(() => {
  if (!menu.children) return []
  return menu.children.filter(
    (c) => c.menuType !== 'F' && c.visible !== '0'
  )
})

/** 是否有可见子菜单 */
const hasVisibleChildren = computed(() => visibleChildren.value.length > 0)

/**
 * 安全解析图标组件名。
 * Element Plus Icons 在 main.ts 中已通过 app.component 全局注册。
 */
const iconComponent = computed(() => {
  if (!menu.icon) return null
  return menu.icon
})
</script>
