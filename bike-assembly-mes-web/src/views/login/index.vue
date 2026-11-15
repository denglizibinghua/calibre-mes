<template>
  <main class="login-page">
    <section class="login-visual">
      <div class="visual-wheel" aria-hidden="true">
        <svg viewBox="0 0 560 560" fill="none">
          <g class="wheel-rotate">
            <circle cx="280" cy="280" r="244" stroke="currentColor" stroke-width="1" opacity="0.38" />
            <circle cx="280" cy="280" r="208" stroke="currentColor" stroke-width="0.8" opacity="0.24" stroke-dasharray="1 9" />
            <circle cx="280" cy="280" r="164" stroke="currentColor" stroke-width="1.2" opacity="0.5" />
            <circle cx="280" cy="280" r="96" stroke="currentColor" stroke-width="1" opacity="0.42" />
            <circle cx="280" cy="280" r="38" stroke="currentColor" stroke-width="1.4" opacity="0.8" />
            <circle cx="280" cy="280" r="8" fill="currentColor" opacity="0.85" />
            <g stroke="currentColor" stroke-width="1" opacity="0.55">
              <path d="M280 36v52M280 472v52M36 280h52M472 280h52" />
              <path d="M108 108l36 36M416 416l36 36M452 108l-36 36M144 416l-36 36" />
              <path d="M280 184l83 96-83 96-83-96 83-96Z" />
              <path d="M280 116v70M280 374v70M172 280h70M318 280h70" />
            </g>
            <path d="M280 36a244 244 0 0 1 196 98" stroke="var(--lux-gold-500)" stroke-width="2" stroke-linecap="round" />
            <circle cx="476" cy="134" r="3" fill="var(--lux-gold-500)" />
          </g>
        </svg>
      </div>

      <header class="login-brand">
        <BrandMark :size="42" />
        <div>
          <strong>自行车装配 MES</strong>
          <small>CALIBRE · MANUFACTURING SYSTEM</small>
        </div>
      </header>

      <div class="login-visual-copy">
        <span class="visual-kicker">PRECISION ASSEMBLY / 精密装配</span>
        <h1>精工于序，<br />装配有道。</h1>
        <p>从一颗螺栓的扭矩，到一条产线的节拍。MES 把每一道工序，变成可测量、可追溯、可交付的秩序。</p>

        <div class="feature-list">
          <div class="feature-item">
            <span class="feature-index">01</span>
            <div>
              <strong>生产可视</strong>
              <small>工单、工序、设备与安灯实时汇总</small>
            </div>
          </div>
          <div class="feature-item">
            <span class="feature-index">02</span>
            <div>
              <strong>流程协同</strong>
              <small>状态机贯通计划、执行与质检</small>
            </div>
          </div>
          <div class="feature-item">
            <span class="feature-index">03</span>
            <div>
              <strong>质量闭环</strong>
              <small>返工复检全链路可追溯</small>
            </div>
          </div>
        </div>
      </div>

      <footer class="login-visual-footer">
        <span>CALIBRE MES · V4.0</span>
        <span>QINGDAO / CN</span>
        <span>EST. 2026</span>
      </footer>
    </section>

    <section class="login-panel">
      <div class="login-card">
        <div class="mobile-brand">
          <BrandMark :size="30" />
          <strong>自行车装配 MES</strong>
        </div>

        <div class="login-card-meta">
          <span class="lux-kicker">SECURE ACCESS</span>
          <span class="login-card-serial">CAL · AUTH / 01</span>
        </div>

        <h2>登录控制台</h2>
        <p class="login-description">使用账号密码或已注册的人脸模板进入制造执行系统。</p>

        <el-radio-group v-model="authMode" class="auth-modes" size="default">
          <el-radio-button value="password">密码</el-radio-button>
          <el-radio-button value="account-face">账号 + 人脸</el-radio-button>
          <el-radio-button value="face">纯人脸</el-radio-button>
        </el-radio-group>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          @submit.prevent="handleLogin"
        >
          <el-form-item v-if="authMode !== 'face'" label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名" prefix-icon="User" />
          </el-form-item>
          <el-form-item v-if="authMode === 'password'" label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <div v-if="authMode !== 'password'" class="face-capture">
            <el-select
              v-if="cameraActive && cameraDevices.length > 1"
              v-model="selectedCameraId"
              class="camera-select"
              size="small"
              aria-label="选择摄像头"
              @change="switchCamera"
            >
              <el-option
                v-for="(device, index) in cameraDevices"
                :key="device.deviceId"
                :label="device.label || `摄像头 ${index + 1}`"
                :value="device.deviceId"
              />
            </el-select>
            <div v-if="cameraActive" class="camera-frame">
              <video ref="videoRef" autoplay muted playsinline disablepictureinpicture />
            </div>
            <small v-if="cameraActive && activeCameraName">
              {{ activeCameraName }}<span v-if="videoResolution"> · {{ videoResolution }}</span>
            </small>
            <el-button v-if="!cameraActive" :disabled="loading" @click="startCamera">
              <el-icon><Camera /></el-icon>
              开启摄像头
            </el-button>
            <el-button v-else type="primary" :loading="loading" @click="captureAndLogin">
              <el-icon><Camera /></el-icon>
              拍摄并登录
            </el-button>
            <small>采集照片仅用于本次识别，不会保存在业务服务器。</small>
          </div>
          <el-form-item class="login-submit-item">
            <el-button v-if="authMode === 'password'" type="primary" native-type="submit" :loading="loading">
              登录系统
              <el-icon class="login-button-icon"><Right /></el-icon>
            </el-button>
          </el-form-item>
        </el-form>

        <div class="security-note">
          <el-icon><Lock /></el-icon>
          <span>人脸识别不可用时，可继续使用账号密码登录</span>
        </div>

        <div class="login-card-foot">
          <span>MES · 制造执行系统</span>
          <span>CALIBRE / V4.0</span>
        </div>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Camera } from '@element-plus/icons-vue'
import axios from 'axios'
import BrandMark from '@/components/BrandMark.vue'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'
import { getCameraAccessErrorMessage } from '@/utils/cameraAccess'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const authMode = ref<'password' | 'account-face' | 'face'>('password')
const videoRef = ref<HTMLVideoElement>()
const cameraActive = ref(false)
const cameraDevices = ref<MediaDeviceInfo[]>([])
const selectedCameraId = ref('')
let cameraStream: MediaStream | null = null
const activeCameraName = computed(() =>
  cameraDevices.value.find(device => device.deviceId === selectedCameraId.value)?.label || '默认摄像头'
)
const videoResolution = computed(() => {
  const video = videoRef.value
  return video?.videoWidth && video?.videoHeight ? `${video.videoWidth} × ${video.videoHeight}` : ''
})

// 与项目种子数据中的演示账号保持一致。
const form = reactive({ username: 'admin', password: 'Demo@123456' })

const rules = computed<FormRules>(() => ({
  username: [{ required: authMode.value !== 'face', message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: authMode.value === 'password', message: '请输入密码', trigger: 'blur' }]
}))

/**
 * 登录并保存双 Token、用户信息及权限状态。
 */
async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true

  try {
    const res = await axios.post('/admin/user/login', form)
    if (res.data?.code === 1) {
      await completeLogin(res.data.data)
    } else {
      ElMessage.error(res.data?.msg || '登录失败')
    }
  } catch (e: any) {
    const message = e.response?.data?.msg
      || (e.code === 'ERR_NETWORK' ? '无法连接 MES 服务，请确认后端已启动。' : '登录失败，请检查账号密码后重试。')
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

async function startCamera(deviceId?: string) {
  try {
    if (!window.isSecureContext) {
      throw new Error('insecure-context')
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new Error('camera-api-unavailable')
    }
    const videoConstraint: MediaTrackConstraints = deviceId
      ? { deviceId: { exact: deviceId } }
      : { facingMode: { ideal: 'user' } }
    try {
      cameraStream = await navigator.mediaDevices.getUserMedia({
        video: videoConstraint,
        audio: false
      })
    } catch (error: any) {
      if (!['OverconstrainedError', 'NotFoundError'].includes(error?.name)) throw error
      cameraStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false })
    }
    const videoTrack = cameraStream.getVideoTracks()[0]
    const settings = videoTrack?.getSettings()
    cameraDevices.value = (await navigator.mediaDevices.enumerateDevices())
      .filter(device => device.kind === 'videoinput')
    selectedCameraId.value = settings?.deviceId || cameraDevices.value[0]?.deviceId || ''
    cameraActive.value = true
    await nextTick()
    const video = videoRef.value
    if (!video) throw new Error('camera-video-unavailable')
    const frameReady = waitForCameraFrame(video)
    video.srcObject = cameraStream
    await Promise.all([video.play(), frameReady])
  } catch (error: any) {
    stopCamera()
    selectedCameraId.value = ''
    const cameraError = error?.message === 'insecure-context'
      ? { name: 'SecurityError' }
      : error?.message === 'camera-api-unavailable'
        ? { name: window.isSecureContext ? 'NotSupportedError' : 'SecurityError' }
        : error?.message === 'camera-frame-timeout' || error?.message === 'camera-video-unavailable'
          ? { name: 'CameraFrameError' }
        : error
    ElMessage.error(getCameraAccessErrorMessage(cameraError, window.isSecureContext))
  }
}

async function switchCamera(deviceId: string) {
  if (!deviceId || deviceId === cameraStream?.getVideoTracks()[0]?.getSettings().deviceId) return
  stopCamera()
  await startCamera(deviceId)
}

function waitForCameraFrame(video: HTMLVideoElement): Promise<void> {
  if (video.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA && video.videoWidth > 0) {
    return Promise.resolve()
  }

  return new Promise((resolve, reject) => {
    const timeout = window.setTimeout(() => finish(new Error('camera-frame-timeout')), 8000)
    const onLoadedData = () => {
      if (video.videoWidth > 0) finish()
    }
    const onError = () => finish(new Error('camera-video-unavailable'))
    const finish = (error?: Error) => {
      window.clearTimeout(timeout)
      video.removeEventListener('loadeddata', onLoadedData)
      video.removeEventListener('error', onError)
      if (error) reject(error)
      else resolve()
    }

    video.addEventListener('loadeddata', onLoadedData)
    video.addEventListener('error', onError)
  })
}

async function captureAndLogin() {
  if (!videoRef.value || !cameraActive.value) return
  if (authMode.value === 'account-face' && !form.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  const video = videoRef.value
  if (!video.videoWidth || !video.videoHeight) {
    ElMessage.warning('摄像头画面尚未就绪')
    return
  }
  const canvas = document.createElement('canvas')
  canvas.width = video.videoWidth
  canvas.height = video.videoHeight
  canvas.getContext('2d')?.drawImage(video, 0, 0)
  const imageBase64 = canvas.toDataURL('image/jpeg', 0.85).split(',')[1]
  loading.value = true
  stopCamera()
  try {
    const res = await axios.post('/admin/user/face-login', {
      username: authMode.value === 'face' ? undefined : form.username.trim(),
      imageBase64
    })
    if (res.data?.code === 1) await completeLogin(res.data.data)
    else ElMessage.error(res.data?.msg || '人脸登录失败')
  } catch (error: any) {
    const message = error.response?.data?.msg
      || (error.code === 'ERR_NETWORK' ? '无法连接 MES 服务，请确认后端已启动。' : '人脸登录失败')
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

async function completeLogin(u: any) {
  const userStore = useUserStore()
  const permissionStore = usePermissionStore()
  if (u.accessToken) localStorage.setItem('token', u.accessToken)
  if (u.refreshToken) localStorage.setItem('refreshToken', u.refreshToken)
  userStore.login({
    id: u.id,
    username: u.username,
    nickname: u.nickname,
    roleId: u.roleId,
    token: u.accessToken || u.token,
    permissions: u.permissions || [],
    dataScope: u.dataScope || 'SELF',
    mustChangePassword: u.mustChangePassword,
    passwordExpired: u.passwordExpired
  })
  permissionStore.resetPermission()
  ElMessage.success('登录成功')
  if (u.mustChangePassword === 1 || u.passwordExpired === true) {
    await router.replace({ path: '/change-password', query: { forced: '1' } })
    return
  }
  await router.replace((route.query.redirect as string) || '/')
}

function stopCamera() {
  cameraStream?.getTracks().forEach(track => track.stop())
  cameraStream = null
  cameraActive.value = false
}

onBeforeUnmount(stopCamera)
</script>

<style scoped lang="scss">
.login-page {
  display: grid;
  min-height: 100vh;
  grid-template-columns: minmax(520px, 1.12fr) minmax(440px, 0.88fr);
  overflow: hidden;
  background: var(--lux-ink-950);
}

/* ---------------------------------------------------------------------
   Visual
   --------------------------------------------------------------------- */
.login-visual {
  position: relative;
  display: flex;
  min-height: 100vh;
  padding: 42px clamp(42px, 5vw, 84px) 32px;
  overflow: hidden;
  flex-direction: column;
  color: var(--lux-shell-text);
  background: #070a0f;
}

.login-visual::before {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgb(198 169 110 / 3.5%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(198 169 110 / 3.5%) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(110% 75% at 20% 20%, #000 0%, transparent 78%);
  content: '';
}

.login-visual::after {
  position: absolute;
  top: -30%;
  right: -20%;
  width: 80%;
  height: 80%;
  background: radial-gradient(circle, rgb(198 169 110 / 12%), transparent 64%);
  content: '';
  pointer-events: none;
}

.visual-wheel {
  position: absolute;
  right: -170px;
  bottom: -160px;
  width: min(720px, 78vw);
  height: min(720px, 78vw);
  color: var(--lux-gold-500);
  opacity: 0.2;
  pointer-events: none;
}

.visual-wheel svg {
  width: 100%;
  height: 100%;
}

.wheel-rotate {
  transform-origin: 280px 280px;
  animation: wheel-spin 90s linear infinite;
}

@keyframes wheel-spin {
  to {
    transform: rotate(360deg);
  }
}

.login-brand {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
}

.login-brand > :deep(.brand-mark) {
  color: #ded7c6;
  --brand-accent: var(--lux-gold-500);
}

.login-brand > div {
  display: flex;
  margin-left: 14px;
  flex-direction: column;

  strong {
    color: #f4f0e5;
    font-family: var(--lux-font-display);
    font-size: 16px;
    font-weight: 500;
    letter-spacing: 0.04em;
  }

  small {
    margin-top: 4px;
    color: #6e6a60;
    font-family: var(--lux-font-data);
    font-size: 8px;
    letter-spacing: 0.22em;
  }
}

.login-visual-copy {
  position: relative;
  z-index: 2;
  width: min(640px, 100%);
  margin: auto 0;
  padding: 64px 0;
}

.visual-kicker {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  color: var(--lux-gold-400);
  font-family: var(--lux-font-data);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.24em;

  &::before {
    width: 28px;
    height: 1px;
    background: var(--lux-gold-500);
    content: '';
  }
}

.login-visual-copy h1 {
  margin: 22px 0 24px;
  color: #f3eee2;
  font-family: var(--lux-font-display);
  font-size: clamp(44px, 5vw, 72px);
  font-weight: 300;
  line-height: 1.18;
  letter-spacing: 0.02em;
}

.login-visual-copy > p {
  max-width: 520px;
  color: #9b958a;
  font-size: 14px;
  line-height: 1.9;
}

.feature-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 46px;
  border-top: 1px solid var(--lux-shell-line);
}

.feature-item {
  display: flex;
  min-width: 0;
  gap: 12px;
  padding: 20px 18px 4px 0;
  border-right: 1px solid var(--lux-shell-line);

  &:last-child {
    border-right: 0;
    padding-right: 0;
  }
}

.feature-index {
  color: var(--lux-gold-600);
  font-family: var(--lux-font-data);
  font-size: 11px;
  letter-spacing: 0.08em;
  line-height: 1.6;
}

.feature-item > div {
  display: flex;
  min-width: 0;
  flex-direction: column;

  strong {
    color: #e7e1d5;
    font-size: 12.5px;
    font-weight: 500;
  }

  small {
    margin-top: 5px;
    color: #7e7a70;
    font-size: 10px;
    line-height: 1.6;
  }
}

.login-visual-footer {
  position: relative;
  z-index: 2;
  display: flex;
  gap: 26px;
  color: #5f5b53;
  font-family: var(--lux-font-data);
  font-size: 8px;
  letter-spacing: 0.18em;
}

/* ---------------------------------------------------------------------
   Panel
   --------------------------------------------------------------------- */
.login-panel {
  position: relative;
  display: flex;
  min-height: 100vh;
  align-items: center;
  justify-content: center;
  padding: 48px;
  overflow: hidden;
  background-color: var(--lux-porcelain-100);
  background-image:
    radial-gradient(90% 60% at 85% 10%, rgb(198 169 110 / 12%), transparent 60%),
    linear-gradient(rgb(20 24 31 / 2.4%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(20 24 31 / 2.4%) 1px, transparent 1px);
  background-size: auto, 40px 40px, 40px 40px;
}

.login-card {
  position: relative;
  width: min(430px, 100%);
  padding: 44px 42px 30px;
  background: var(--lux-surface);
  border: 1px solid var(--lux-line);
  border-radius: var(--lux-radius-md);
  box-shadow: var(--lux-shadow-page);
}

.login-card::before,
.login-card::after {
  position: absolute;
  width: 16px;
  height: 16px;
  content: '';
  pointer-events: none;
}

.login-card::before {
  top: -1px;
  left: -1px;
  border-top: 1px solid var(--lux-gold-500);
  border-left: 1px solid var(--lux-gold-500);
}

.login-card::after {
  right: -1px;
  bottom: -1px;
  border-right: 1px solid var(--lux-gold-500);
  border-bottom: 1px solid var(--lux-gold-500);
}

.mobile-brand {
  display: none;
}

.login-card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.login-card-serial {
  color: var(--lux-text-faint);
  font-family: var(--lux-font-data);
  font-size: 8px;
  letter-spacing: 0.16em;
}

.login-card h2 {
  margin: 10px 0 8px;
  color: var(--lux-text-strong);
  font-family: var(--lux-font-display);
  font-size: 28px;
  font-weight: 500;
  letter-spacing: 0.02em;
}

.login-description {
  margin-bottom: 26px;
  color: var(--lux-text-muted);
  font-size: 12.5px;
  line-height: 1.7;
}

.auth-modes {
  display: flex;
  width: 100%;
  margin: 0 0 22px;
}

.auth-modes :deep(.el-radio-button) {
  flex: 1;
}

.auth-modes :deep(.el-radio-button__inner) {
  width: 100%;
  min-height: 38px;
  padding: 9px 8px;
  color: var(--lux-text-muted);
  font-size: 12px;
  font-weight: 500;
  background: var(--lux-surface);
  border-color: var(--lux-line);
  border-radius: 0;
  box-shadow: none;
}

.auth-modes :deep(.el-radio-button:first-child .el-radio-button__inner) {
  border-radius: var(--lux-radius-xs) 0 0 var(--lux-radius-xs);
}

.auth-modes :deep(.el-radio-button:last-child .el-radio-button__inner) {
  border-radius: 0 var(--lux-radius-xs) var(--lux-radius-xs) 0;
}

.auth-modes :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  color: #17130b;
  background: linear-gradient(135deg, #e2cd9e, #c6a96e);
  border-color: var(--lux-gold-600);
  box-shadow: -1px 0 0 0 var(--lux-gold-600);
}

.face-capture {
  display: grid;
  justify-items: start;
  gap: 10px;
  margin: 0 0 18px;
}

.camera-frame {
  position: relative;
  width: 100%;
}

.camera-frame::before,
.camera-frame::after {
  position: absolute;
  z-index: 1;
  width: 14px;
  height: 14px;
  content: '';
  pointer-events: none;
}

.camera-frame::before {
  top: 6px;
  left: 6px;
  border-top: 1px solid var(--lux-gold-400);
  border-left: 1px solid var(--lux-gold-400);
}

.camera-frame::after {
  right: 6px;
  bottom: 6px;
  border-right: 1px solid var(--lux-gold-400);
  border-bottom: 1px solid var(--lux-gold-400);
}

.face-capture video {
  width: 100%;
  max-height: 250px;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  transform: scaleX(-1);
  background: #0b0f15;
  border: 1px solid var(--lux-line);
  border-radius: var(--lux-radius-xs);
}

.camera-select {
  width: 100%;
}

.face-capture small {
  color: var(--lux-text-muted);
  font-size: 10.5px;
  line-height: 1.55;
}

:deep(.el-form-item__label) {
  padding-bottom: 7px;
  color: var(--lux-text-muted);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.04em;
}

:deep(.el-input__wrapper) {
  min-height: 46px;
  padding: 0 14px;
  background: var(--lux-surface-soft);
  border-radius: var(--lux-radius-xs);
  box-shadow: 0 0 0 1px var(--lux-line) inset;
}

:deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #bfb5a2 inset;
}

:deep(.el-input__wrapper.is-focus) {
  background: var(--lux-surface);
  box-shadow: 0 0 0 1px var(--lux-gold-500) inset, 0 0 0 4px rgb(198 169 110 / 14%);
}

.login-submit-item {
  margin-top: 8px;
  margin-bottom: 0;
}

.login-submit-item :deep(.el-form-item__content),
.login-submit-item :deep(.el-button) {
  width: 100%;
}

.login-submit-item :deep(.el-button) {
  min-height: 46px;
  font-size: 13.5px;
  font-weight: 600;
  letter-spacing: 0.06em;
  border-radius: var(--lux-radius-xs);
}

.login-button-icon {
  margin-left: 8px;
}

.security-note {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  margin-top: 22px;
  padding-top: 16px;
  color: var(--lux-text-faint);
  font-size: 10px;
  border-top: 1px solid var(--lux-line-soft);
}

.login-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 18px;
  color: var(--lux-text-faint);
  font-family: var(--lux-font-data);
  font-size: 8px;
  letter-spacing: 0.14em;
}

/* ---------------------------------------------------------------------
   Responsive
   --------------------------------------------------------------------- */
@media (max-width: 1200px) {
  .login-page {
    grid-template-columns: minmax(460px, 1fr) minmax(430px, 0.92fr);
  }

  .feature-list {
    grid-template-columns: 1fr;
    border-top: 0;
  }

  .feature-item {
    padding: 8px 0;
    border-right: 0;
    border-top: 1px solid var(--lux-shell-line);
  }

  .feature-item:first-child {
    border-top: 0;
  }
}

@media (max-width: 860px) {
  .login-page {
    display: block;
    background: var(--lux-porcelain-100);
  }

  .login-visual {
    display: none;
  }

  .login-panel {
    min-height: 100vh;
    padding: 24px;
  }

  .mobile-brand {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 28px;
    color: var(--lux-text-strong);

    strong {
      font-family: var(--lux-font-display);
      font-size: 14px;
      font-weight: 500;
    }
  }

  .mobile-brand :deep(.brand-mark) {
    color: var(--lux-text-strong);
    --brand-accent: var(--lux-gold-600);
  }
}

@media (max-width: 480px) {
  .login-panel {
    padding: 16px;
  }

  .login-card {
    padding: 32px 24px 24px;
  }

  .login-card h2 {
    font-size: 24px;
  }
}
</style>
