/**
 * 网络状态检测 — V3.3。
 * 提供在线/弱网/离线检测、监听和状态展示。
 */

import { ref } from 'vue'

export type NetworkStatus = 'online' | 'weak' | 'offline'

const status = ref<NetworkStatus>('online')
const showNetworkBar = ref(false)
let lastOnline = true

/**
 * 初始化网络监听（App.vue onLaunch 中调用）。
 */
export function initNetworkMonitor(): void {
  // 初始检测
  uni.getNetworkType({
    success: (res) => {
      updateStatus(res.networkType)
    },
  })

  // 监听变化
  uni.onNetworkStatusChange((res) => {
    updateStatus(res.networkType)
  })
}

function updateStatus(networkType: string): void {
  switch (networkType) {
    case 'none':
    case 'unknown':
      status.value = 'offline'
      showNetworkBar.value = true
      break
    case '2g':
    case '3g':
      status.value = 'weak'
      showNetworkBar.value = true
      break
    case '4g':
    case '5g':
    case 'wifi':
    case 'ethernet':
      status.value = 'online'
      showNetworkBar.value = !lastOnline // 从离线恢复到在线时短暂提示
      break
  }

  if (status.value === 'online') {
    setTimeout(() => { showNetworkBar.value = false }, 3000)
  }
  lastOnline = status.value === 'online'
}

/** 获取当前网络状态 */
export function getNetworkStatus(): NetworkStatus {
  return status.value
}

/** 是否在线 */
export function isOnline(): boolean {
  return status.value === 'online' || status.value === 'weak'
}

/** 是否离线 */
export function isOffline(): boolean {
  return status.value === 'offline'
}

/** 检查网络（返回 Promise） */
export function checkNetwork(): Promise<boolean> {
  return new Promise((resolve) => {
    uni.getNetworkType({
      success: (res) => {
        updateStatus(res.networkType)
        resolve(isOnline())
      },
      fail: () => resolve(false),
    })
  })
}

export { status, showNetworkBar }
