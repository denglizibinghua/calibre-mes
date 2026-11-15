/** 自定义页面内容从微信胶囊下方开始，避免状态栏和系统按钮遮挡。 */
export function getCustomContentTop(): number {
  const info = uni.getSystemInfoSync()
  const statusBarHeight = info.statusBarHeight || 0
  let top = statusBarHeight + 44
  // #ifdef MP-WEIXIN
  try {
    const menu = uni.getMenuButtonBoundingClientRect()
    if (menu && menu.height > 0 && menu.top >= statusBarHeight) {
      top = menu.bottom + Math.max(menu.top - statusBarHeight, 8)
    }
  } catch {
    // 开发者工具未返回胶囊尺寸时使用标准导航栏高度。
  }
  // #endif
  return top
}
