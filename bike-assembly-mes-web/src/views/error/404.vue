<template>
  <div class="error-page">
    <div class="error-grid" aria-hidden="true" />
    <div class="error-glow" aria-hidden="true" />

    <header class="error-brand">
      <BrandMark :size="36" />
      <div>
        <strong>自行车装配 MES</strong>
        <small>CALIBRE · MANUFACTURING</small>
      </div>
    </header>

    <main class="error-card">
      <span class="error-kicker">PAGE NOT FOUND / 页面未找到</span>
      <h1>404</h1>
      <p class="error-title">抱歉，您访问的页面不存在</p>
      <p class="error-sub">链接可能已失效或地址输入有误。返回首页继续管理生产任务。</p>
      <div class="actions">
        <el-button type="primary" @click="goHome">返回首页</el-button>
      </div>
    </main>

    <footer class="error-foot">
      <span>CALIBRE MES · V4.0</span>
      <span>QINGDAO / CN</span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import BrandMark from '@/components/BrandMark.vue'

const router = useRouter()
function goHome() {
  router.replace('/')
}
</script>

<style scoped lang="scss">
.error-page {
  position: relative;
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  padding: 38px clamp(28px, 5vw, 72px) 28px;
  overflow: hidden;
  color: var(--lux-shell-text);
  background-color: var(--lux-ink-950);
}

.error-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgb(198 169 110 / 3%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(198 169 110 / 3%) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(100% 80% at 50% 20%, #000 0%, transparent 80%);
  pointer-events: none;
}

.error-glow {
  position: absolute;
  top: 12%;
  left: 50%;
  width: 620px;
  height: 620px;
  background: radial-gradient(circle, rgb(198 169 110 / 10%), transparent 62%);
  transform: translateX(-50%);
  pointer-events: none;
}

.error-brand {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  color: #ded7c6;
}

.error-brand :deep(.brand-mark) {
  --brand-accent: var(--lux-gold-500);
}

.error-brand > div {
  display: flex;
  flex-direction: column;

  strong {
    color: #f4f0e5;
    font-family: var(--lux-font-display);
    font-size: 15px;
    font-weight: 500;
    letter-spacing: 0.04em;
  }

  small {
    margin-top: 3px;
    color: #6e6a60;
    font-family: var(--lux-font-data);
    font-size: 8px;
    letter-spacing: 0.2em;
  }
}

.error-card {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.error-kicker {
  color: var(--lux-gold-500);
  font-family: var(--lux-font-data);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.24em;
}

.error-card h1 {
  margin: 10px 0 8px;
  color: var(--lux-gold-400);
  font-family: var(--lux-font-display);
  font-size: clamp(96px, 14vw, 168px);
  font-weight: 300;
  line-height: 1;
  letter-spacing: 0.02em;
}

.error-title {
  color: #ede8dc;
  font-family: var(--lux-font-display);
  font-size: clamp(18px, 2vw, 22px);
  font-weight: 400;
  letter-spacing: 0.04em;
}

.error-sub {
  max-width: 460px;
  margin-top: 10px;
  color: #8b857a;
  font-size: 12px;
  line-height: 1.8;
}

.actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 28px;
}

.actions :deep(.el-button) {
  min-width: 130px;
  min-height: 38px;
}

.error-foot {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  gap: 24px;
  color: #5f5b53;
  font-family: var(--lux-font-data);
  font-size: 8px;
  letter-spacing: 0.18em;
}

@media (max-width: 640px) {
  .error-page {
    padding: 24px 18px 20px;
  }

  .error-brand > div {
    display: none;
  }

  .error-foot {
    justify-content: center;
  }
}
</style>
