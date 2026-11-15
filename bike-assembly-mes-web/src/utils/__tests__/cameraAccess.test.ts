import { describe, expect, it } from 'vitest'
import { getCameraAccessErrorMessage } from '../cameraAccess'

describe('getCameraAccessErrorMessage', () => {
  it('explains insecure camera contexts', () => {
    expect(getCameraAccessErrorMessage(new Error(), false)).toContain('HTTPS')
  })

  it('explains how to recover from a denied camera permission', () => {
    expect(getCameraAccessErrorMessage({ name: 'NotAllowedError' }, true)).toContain('允许摄像头')
    expect(getCameraAccessErrorMessage(new Error('Permission denied'), true)).toContain('允许摄像头')
  })

  it('reports missing and busy camera devices separately', () => {
    expect(getCameraAccessErrorMessage({ name: 'NotFoundError' }, true)).toContain('没有检测到')
    expect(getCameraAccessErrorMessage({ name: 'NotReadableError' }, true)).toContain('占用')
  })

  it('explains when permission is granted but no video frame arrives', () => {
    expect(getCameraAccessErrorMessage({ name: 'CameraFrameError' }, true)).toContain('没有画面')
  })

  it('explains when a selected camera cannot start in compatible mode', () => {
    expect(getCameraAccessErrorMessage({ name: 'OverconstrainedError' }, true)).toContain('自动兼容模式')
  })
})
