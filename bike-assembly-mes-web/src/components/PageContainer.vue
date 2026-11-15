<template>
  <section class="page-container">
    <header v-if="$slots.header || title" class="page-header">
      <div class="page-heading">
        <div class="page-meta">
          <span class="page-serial">{{ moduleInfo.serial }}</span>
          <span class="page-module">{{ moduleInfo.label }}</span>
          <span class="page-stamp">{{ stamp }}</span>
        </div>
        <h1>{{ title }}</h1>
        <p v-if="description">{{ description }}</p>
      </div>
      <div v-if="$slots.header" class="page-header-actions">
        <slot name="header" />
      </div>
    </header>
    <slot name="search" />
    <slot />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

defineProps<{
  title?: string
  description?: string
}>()

const route = useRoute()

const MODULES: Record<string, { serial: string; label: string }> = {
  base: { serial: 'CAL · 01', label: 'MASTER DATA / 主数据' },
  production: { serial: 'CAL · 02', label: 'PRODUCTION / 生产执行' },
  quality: { serial: 'CAL · 03', label: 'QUALITY / 质量管控' },
  warehouse: { serial: 'CAL · 04', label: 'WAREHOUSE / 仓储管理' },
  system: { serial: 'CAL · 05', label: 'SYSTEM / 系统治理' },
  report: { serial: 'CAL · 06', label: 'INSIGHT / 报表中心' },
  ai: { serial: 'CAL · 07', label: 'INTELLIGENCE / 智能中枢' },
  account: { serial: 'CAL · 08', label: 'ACCESS / 账号安全' }
}

const moduleInfo = computed(() => {
  const segment = route.path.split('/').filter(Boolean)[0] || ''
  return MODULES[segment] || { serial: 'CAL · 00', label: 'MES / 制造执行' }
})

const stamp = computed(() => {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}.${pad(now.getMonth() + 1)}.${pad(now.getDate())}`
})
</script>
