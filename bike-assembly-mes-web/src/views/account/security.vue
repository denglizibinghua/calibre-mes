<template>
  <PageContainer title="账号安全" description="管理当前账号的人脸登录模板。">
    <section class="security-content">
      <div class="status-row">
        <div>
          <h2>人脸登录</h2>
          <p>{{ registered ? '当前账号已注册人脸模板。' : '当前账号尚未注册人脸模板。' }}</p>
        </div>
        <el-tag :type="registered ? 'success' : 'info'">{{ registered ? '已注册' : '未注册' }}</el-tag>
      </div>

      <div class="camera-area">
        <video v-if="cameraActive" ref="videoRef" autoplay muted playsinline />
        <div v-else class="camera-placeholder">
          <el-icon><Camera /></el-icon>
          <span>摄像头未开启</span>
        </div>
      </div>

      <el-checkbox v-model="consent">
        我同意采集摄像头画面用于人脸识别，并知悉服务器仅保存加密特征模板，不保存原始照片。
      </el-checkbox>
      <div class="actions">
        <el-button v-if="!cameraActive" :disabled="busy" @click="startCamera">
          <el-icon><Camera /></el-icon>
          开启摄像头
        </el-button>
        <el-button v-else type="primary" :loading="busy" :disabled="!consent" @click="register">
          <el-icon><Camera /></el-icon>
          {{ registered ? '重新注册' : '注册人脸模板' }}
        </el-button>
        <el-button v-if="registered" type="danger" plain :disabled="busy" @click="revoke">
          撤销人脸登录
        </el-button>
      </div>
      <p class="notice">人脸识别服务不可用、活体检测失败或模板加密未配置时，系统会拒绝人脸操作；账号密码登录不受影响。</p>
    </section>
  </PageContainer>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Camera } from '@element-plus/icons-vue'
import PageContainer from '@/components/PageContainer.vue'
import { getFaceTemplateStatus, registerFaceTemplate, revokeFaceTemplate } from '@/api/face'

const registered = ref(false)
const consent = ref(false)
const busy = ref(false)
const cameraActive = ref(false)
const videoRef = ref<HTMLVideoElement>()
let stream: MediaStream | null = null

onMounted(async () => {
  registered.value = await getFaceTemplateStatus().catch(() => false)
})
onBeforeUnmount(stopCamera)

async function startCamera() {
  try {
    if (!navigator.mediaDevices?.getUserMedia) throw new Error('当前浏览器不支持摄像头访问')
    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' }, audio: false })
    cameraActive.value = true
    await new Promise(resolve => requestAnimationFrame(() => resolve(undefined)))
    if (videoRef.value) videoRef.value.srcObject = stream
  } catch (error: any) {
    stopCamera()
    ElMessage.error(error?.message || '摄像头启动失败')
  }
}

async function register() {
  if (!consent.value) return ElMessage.warning('请先同意人脸信息处理说明')
  const video = videoRef.value
  if (!video?.videoWidth || !video.videoHeight) return ElMessage.warning('摄像头画面尚未就绪')
  const canvas = document.createElement('canvas')
  canvas.width = video.videoWidth
  canvas.height = video.videoHeight
  canvas.getContext('2d')?.drawImage(video, 0, 0)
  const imageBase64 = canvas.toDataURL('image/jpeg', 0.85).split(',')[1]
  busy.value = true
  stopCamera()
  try {
    await registerFaceTemplate({ imageBase64, consent: true })
    registered.value = true
    consent.value = false
    ElMessage.success('人脸模板已注册')
  } finally {
    busy.value = false
  }
}

async function revoke() {
  try {
    await ElMessageBox.confirm('撤销后将不能使用人脸登录，账号密码登录仍可使用。', '撤销人脸模板', {
      type: 'warning',
      confirmButtonText: '确认撤销',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  busy.value = true
  try {
    await revokeFaceTemplate()
    registered.value = false
    ElMessage.success('人脸模板已撤销')
  } finally {
    busy.value = false
  }
}

function stopCamera() {
  stream?.getTracks().forEach(track => track.stop())
  stream = null
  cameraActive.value = false
}
</script>

<style scoped>
.security-content {
  max-width: 720px;
  padding: 20px;
  background: var(--mes-bg-card, #fff);
  border: 1px solid var(--mes-border, #e5e7eb);
  border-radius: var(--mes-radius-lg, 14px);
}

.status-row,
.actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.status-row h2 {
  margin: 0;
  color: var(--mes-text-primary, #172033);
  font-size: 15px;
  font-weight: 650;
}

.status-row p,
.notice {
  color: var(--mes-text-muted, #7b8a9e);
  font-size: 12px;
  line-height: 1.6;
}

.camera-area {
  display: grid;
  width: min(100%, 420px);
  aspect-ratio: 4 / 3;
  margin: 22px 0 16px;
  overflow: hidden;
  background: #111827;
  border-radius: 6px;
}

.camera-area video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.camera-placeholder {
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 10px;
  color: #d1d5db;
}

.camera-placeholder .el-icon {
  font-size: 28px;
}

.actions {
  justify-content: flex-start;
  flex-wrap: wrap;
  margin-top: 18px;
}

@media (max-width: 640px) {
  .security-content {
    padding: 14px;
  }

  .status-row {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
