import type { Directive, DirectiveBinding } from 'vue'
import { usePermissionStore } from '@/stores/permission'

/**
 * v-permission 自定义指令。
 *
 * 用法：
 *   <el-button v-permission="'admin:user:add'">新增</el-button>
 *   <el-button v-permission="['admin:user:edit', 'admin:user:update']">修改</el-button>
 *
 * 无权限时移除元素 DOM。
 */
export const permission: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    checkPermission(el, binding)
  },
  updated(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    checkPermission(el, binding)
  }
}

function checkPermission(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
  const { value } = binding
  if (!value) return

  const store = usePermissionStore()

  let hasPerm = false
  if (Array.isArray(value)) {
    // 数组：任一权限满足即显示
    hasPerm = store.hasAnyPermission(...value)
  } else {
    hasPerm = store.hasPermission(value)
  }

  if (!hasPerm) {
    // 移除元素
    if (el.parentNode) {
      el.parentNode.removeChild(el)
    } else {
      // 如果已无父节点，用隐藏兜底
      el.style.display = 'none'
    }
  }
}

export default permission
