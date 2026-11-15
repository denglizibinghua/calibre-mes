/**
 * TabBar 共享状态。
 * 首页 → 0，我的 → 1。
 */
import { ref } from 'vue'

/** 当前选中的 tab 索引：0=首页, 1=我的 */
export const activeTabIndex = ref(0)

/** Tab 页面路径列表 */
export const TAB_PAGES = [
  '/pages/home/index',
  '/pages/profile/index',
]

/** Tab 页面路由映射 */
const TAB_ROUTES = [
  'pages/home/index',
  'pages/profile/index',
]

/**
 * 切换到指定 tab 页面。
 */
export function switchToTab(index: number): void {
  if (index === activeTabIndex.value) return
  activeTabIndex.value = index
  uni.reLaunch({ url: TAB_PAGES[index] })
}

/**
 * 根据当前页面路由更新选中索引。
 * 每个 tab 页面在 onShow 中调用。
 */
export function updateActiveTab(): void {
  const pages = getCurrentPages()
  if (pages.length > 0) {
    const route = pages[pages.length - 1].route
    if (!route) return
    const idx = TAB_ROUTES.indexOf(route)
    if (idx !== -1 && idx !== activeTabIndex.value) {
      activeTabIndex.value = idx
    }
  }
}
