<template>
  <div class="change-password-page">
    <div class="change-password-grid" aria-hidden="true" />

    <section class="change-password-card">
      <div class="change-password-brand">
        <BrandMark :size="34" />
        <div>
          <strong>自行车装配 MES</strong>
          <small>CALIBRE · SECURITY</small>
        </div>
      </div>

      <div class="change-password-meta">
        <span class="lux-kicker">SECURE CREDENTIAL</span>
        <span class="change-password-serial">CAL · AUTH / 02</span>
      </div>

      <h2>{{ isForcedChange ? '更新登录凭据' : '修改密码' }}</h2>
      <p class="change-password-desc">
        {{ isForcedChange ? '当前账号需要更新密码后才能继续使用系统。' : '定期更新密码，保持账号与生产数据安全。' }}
      </p>

      <el-alert
        title="密码策略"
        type="info"
        :closable="false"
        show-icon
        class="policy-alert"
      >
        <template #default>
          <ul class="policy-list">
            <li>长度 8–64 位</li>
            <li>必须包含数字、小写字母、大写字母和特殊字符</li>
            <li>不能包含用户名</li>
            <li>不能与近期使用过的密码相同</li>
          </ul>
        </template>
      </el-alert>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        class="change-password-form"
        @submit.prevent="handleSubmit"
      >
        <el-form-item v-if="!isForcedChange" label="原密码" prop="oldPassword">
          <el-input
            v-model="form.oldPassword"
            type="password"
            placeholder="请输入原密码"
            show-password
            autocomplete="off"
          />
        </el-form-item>

        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            placeholder="请输入新密码"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>

        <el-form-item class="submit-item">
          <el-button
            type="primary"
            native-type="submit"
            :loading="submitting"
            class="submit-button"
          >
            {{ isForcedChange ? '修改密码并重新登录' : '修改密码' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="change-password-foot">
        <span>MES · 制造执行系统</span>
        <span>CALIBRE / V4.0</span>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import BrandMark from '@/components/BrandMark.vue'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'

const router = useRouter()
const userStore = useUserStore()
const permissionStore = usePermissionStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const isForcedChange = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const validateConfirmPassword = (_rule: any, value: string, callback: any) => {
  if (value !== form.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = computed<FormRules>(() => {
  const baseRules: FormRules = {
    newPassword: [
      { required: true, message: '请输入新密码', trigger: 'blur' },
      { min: 8, max: 64, message: '密码长度 8-64 位', trigger: 'blur' },
    ],
    confirmPassword: [
      { required: true, message: '请确认新密码', trigger: 'blur' },
      { validator: validateConfirmPassword, trigger: 'blur' },
    ],
  }
  if (!isForcedChange.value) {
    baseRules.oldPassword = [
      { required: true, message: '请输入原密码', trigger: 'blur' },
    ]
  }
  return baseRules
})

onMounted(() => {
  // 检查是否为强制改密场景
  const userInfo = localStorage.getItem('userInfo')
  if (userInfo) {
    try {
      const u = JSON.parse(userInfo)
      if (u.mustChangePassword === 1 || u.passwordExpired === true) {
        isForcedChange.value = true
      }
    } catch {
      // ignore
    }
  }
})

// 检查 URL 参数
const query = new URLSearchParams(window.location.search)
if (query.get('forced') === '1') {
  isForcedChange.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const body: Record<string, string> = {
      newPassword: form.newPassword,
    }
    if (!isForcedChange.value) {
      body.oldPassword = form.oldPassword
    }

    await request.put('/admin/sys-user/change-password', body)
    ElMessage.success('密码修改成功，请重新登录')

    // 清除登录状态
    userStore.logout()
    permissionStore.resetPermission()
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('userInfo')

    // 跳转登录页
    setTimeout(() => {
      router.replace({ path: '/login', query: { reason: 'password_changed' } })
    }, 800)
  } catch {
    // 错误已在拦截器中处理
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.change-password-page {
  position: relative;
  display: flex;
  min-height: 100vh;
  align-items: center;
  justify-content: center;
  padding: 32px;
  overflow: hidden;
  background-color: var(--lux-ink-950);
  background-image:
    radial-gradient(80% 60% at 50% 0%, rgb(198 169 110 / 12%), transparent 62%),
    linear-gradient(rgb(198 169 110 / 3%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(198 169 110 / 3%) 1px, transparent 1px);
  background-size: auto, 44px 44px, 44px 44px;
}

.change-password-grid {
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 50% 50%, rgb(198 169 110 / 8%), transparent 55%);
  pointer-events: none;
}

.change-password-card {
  position: relative;
  z-index: 1;
  width: min(460px, 100%);
  padding: 38px 40px 26px;
  background: var(--lux-surface);
  border: 1px solid var(--lux-line);
  border-radius: var(--lux-radius-md);
  box-shadow: var(--lux-shadow-dialog);
}

.change-password-card::before,
.change-password-card::after {
  position: absolute;
  width: 16px;
  height: 16px;
  content: '';
  pointer-events: none;
}

.change-password-card::before {
  top: -1px;
  left: -1px;
  border-top: 1px solid var(--lux-gold-500);
  border-left: 1px solid var(--lux-gold-500);
}

.change-password-card::after {
  right: -1px;
  bottom: -1px;
  border-right: 1px solid var(--lux-gold-500);
  border-bottom: 1px solid var(--lux-gold-500);
}

.change-password-brand {
  display: flex;
  align-items: center;
  gap: 11px;
  margin-bottom: 24px;
}

.change-password-brand :deep(.brand-mark) {
  color: var(--lux-text-strong);
  --brand-accent: var(--lux-gold-600);
}

.change-password-brand > div {
  display: flex;
  flex-direction: column;

  strong {
    color: var(--lux-text-strong);
    font-family: var(--lux-font-display);
    font-size: 14px;
    font-weight: 500;
    letter-spacing: 0.04em;
  }

  small {
    margin-top: 3px;
    color: var(--lux-text-faint);
    font-family: var(--lux-font-data);
    font-size: 8px;
    letter-spacing: 0.2em;
  }
}

.change-password-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.change-password-serial {
  color: var(--lux-text-faint);
  font-family: var(--lux-font-data);
  font-size: 8px;
  letter-spacing: 0.16em;
}

.change-password-card h2 {
  color: var(--lux-text-strong);
  font-family: var(--lux-font-display);
  font-size: 28px;
  font-weight: 500;
  letter-spacing: 0.02em;
  text-align: left;
}

.change-password-desc {
  margin: 8px 0 22px;
  color: var(--lux-text-muted);
  font-size: 12.5px;
  line-height: 1.7;
}

.policy-alert {
  margin-bottom: 20px;
}

.policy-list {
  margin: 0;
  padding-left: 18px;
  color: var(--lux-text-muted);
  font-size: 11.5px;
  line-height: 1.8;
}

.change-password-form :deep(.el-form-item__label) {
  padding-bottom: 6px;
  color: var(--lux-text-muted);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.04em;
}

.change-password-form :deep(.el-input__wrapper) {
  min-height: 44px;
  background: var(--lux-surface-soft);
}

.submit-item {
  margin-top: 4px;
  margin-bottom: 0;
}

.submit-item :deep(.el-form-item__content),
.submit-button {
  width: 100%;
}

.submit-button {
  min-height: 44px;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.change-password-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 24px;
  padding-top: 16px;
  color: var(--lux-text-faint);
  font-family: var(--lux-font-data);
  font-size: 8px;
  letter-spacing: 0.14em;
  border-top: 1px solid var(--lux-line-soft);
}

@media (max-width: 520px) {
  .change-password-page {
    padding: 16px;
  }

  .change-password-card {
    padding: 28px 22px 22px;
  }

  .change-password-card h2 {
    font-size: 24px;
  }
}
</style>
