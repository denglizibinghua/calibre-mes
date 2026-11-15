export function getCameraAccessErrorMessage(error: unknown, secureContext: boolean): string {
  if (!secureContext) {
    return '摄像头需要安全连接。请使用 http://localhost 访问，或为局域网地址配置 HTTPS 后重试。'
  }

  const cameraError = error as { name?: string; message?: string }
  const errorMessage = cameraError?.message?.toLowerCase() || ''
  if (errorMessage.includes('permission denied') || errorMessage.includes('permission was denied')) {
    return '摄像头权限被拒绝。请在浏览器地址栏的网站权限中允许摄像头，并检查 Windows“设置 > 隐私和安全性 > 摄像头”后重试。'
  }

  switch (cameraError?.name) {
    case 'NotAllowedError':
    case 'PermissionDeniedError':
      return '摄像头权限被拒绝。请在浏览器地址栏的网站权限中允许摄像头，并检查 Windows“设置 > 隐私和安全性 > 摄像头”后重试。'
    case 'NotFoundError':
    case 'DevicesNotFoundError':
      return '没有检测到可用摄像头，请连接摄像头后重试。'
    case 'NotReadableError':
    case 'TrackStartError':
      return '摄像头可能正被其他应用占用，请关闭占用摄像头的应用后重试。'
    case 'OverconstrainedError':
      return '所选摄像头无法启动，自动兼容模式也未成功。请重新选择摄像头或检查设备驱动后重试。'
    case 'NotSupportedError':
      return '当前浏览器不支持摄像头访问，请使用最新版 Chrome 或 Edge。'
    case 'CameraFrameError':
      return '摄像头已连接但没有画面，请检查镜头遮挡或 Windows 摄像头隐私设置后重试。'
    case 'SecurityError':
      return '浏览器安全策略阻止了摄像头访问，请改用 localhost 或 HTTPS 地址。'
    default:
      return '无法开启摄像头，请检查浏览器权限和设备连接后重试。'
  }
}
