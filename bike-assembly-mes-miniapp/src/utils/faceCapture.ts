/**
 * 相机采集人脸照片，只返回 Base64；本地不保留副本。
 * 非微信小程序环境直接提示并返回 null。
 */
export async function captureFaceImage(): Promise<string | null> {
  // #ifndef MP-WEIXIN
  uni.showToast({ title: '请在微信小程序中使用相机识别', icon: 'none' })
  return null
  // #endif
  return new Promise((resolve) => {
    uni.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['camera'],
      camera: 'front',
      success: (result) => {
        const file = result.tempFiles?.[0]
        if (!file?.tempFilePath) return resolve(null)
        uni.getFileSystemManager().readFile({
          filePath: file.tempFilePath,
          encoding: 'base64',
          success: (readResult) => resolve(String(readResult.data)),
          fail: () => resolve(null),
        })
      },
      fail: () => resolve(null),
    })
  })
}
