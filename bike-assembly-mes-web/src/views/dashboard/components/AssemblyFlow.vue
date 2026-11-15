<template>
  <div class="assembly-flow">
    <div v-if="!processes.length" class="screen-empty stage-empty">
      <svg viewBox="0 0 160 90" fill="none" aria-hidden="true"><path d="M15 68h130M28 68V40h25V22h54v18h25v28M63 68V49h34v19M66 33h5m18 0h5" stroke="currentColor" stroke-width="2" /></svg>
      <strong>暂无在制工序</strong><span>下达工单并生成工序后，这里将展示装配负荷</span>
    </div>
    <template v-else>
      <div class="stage-grid" role="group" aria-label="装配工序，点击查看负荷">
        <button v-for="(stage, index) in visibleProcesses" :key="stage.processId" type="button"
          class="assembly-stage" :class="{ 'is-selected': selectedId === stage.processId, 'has-alert': stage.abnormal > 0 }"
          :aria-pressed="selectedId === stage.processId" @click="$emit('select', stage.processId)">
          <div class="stage-top"><span class="stage-number">{{ String(page * 5 + index + 1).padStart(2, '0') }}</span>
            <span class="stage-state"><i :class="{ 'is-warning': stage.abnormal > 0, 'is-idle': !stage.running && !stage.abnormal }" />{{ stage.abnormal ? '需关注' : stage.running ? '作业中' : stage.waiting ? '待检验' : stage.completed === stage.total ? '已完成' : '待流转' }}</span>
          </div>
          <svg class="station-drawing" viewBox="0 0 180 72" fill="none" aria-hidden="true">
            <path d="M23 55h134M35 55V30h110v25M45 56v9m90-9v9M55 30V17h70v13M66 17V9h48v8M45 39h14m62 0h14M72 49l13-16 13 16H72Zm13-16h15l11 16M98 49l13-16m-30 0h9" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
            <circle cx="72" cy="49" r="9" stroke="currentColor" stroke-width="1.4" /><circle cx="111" cy="49" r="9" stroke="currentColor" stroke-width="1.4" />
          </svg>
          <h3 :title="stage.processName">{{ stage.processName }}</h3>
          <div class="stage-figures"><span><b>{{ stage.running }}</b> 作业</span><span><b>{{ stage.waiting }}</b> 待检</span><span :class="{ 'signal-warning': stage.abnormal > 0 }"><b>{{ stage.abnormal }}</b> 异常</span></div>
          <span class="stage-load" aria-hidden="true"><i :style="{ width: `${stage.total ? (stage.completed / stage.total) * 100 : 0}%` }" /></span>
        </button>
      </div>
      <div v-if="processes.length > 5" class="stage-pagination" aria-label="工序分页"><span>第 {{ page * 5 + 1 }}–{{ Math.min((page + 1) * 5, processes.length) }} 道 / 共 {{ processes.length }} 道</span><div><button type="button" :disabled="page === 0" aria-label="上一组工序" @click="changePage(-1)">上一组</button><button type="button" :disabled="(page + 1) * 5 >= processes.length" aria-label="下一组工序" @click="changePage(1)">下一组</button></div></div>
      <div v-if="selected" class="stage-inspector" aria-live="polite">
        <div><span class="muted">选中工序</span><strong>{{ selected.processName }}</strong></div>
        <dl><div><dt>待开始</dt><dd>{{ selected.queued }}</dd></div><div><dt>进行中</dt><dd>{{ selected.running }}</dd></div><div><dt>待检验</dt><dd>{{ selected.waiting }}</dd></div><div><dt>不合格 / 返工</dt><dd :class="{ 'signal-warning': selected.abnormal > 0 }">{{ selected.abnormal }}</dd></div><div><dt>已完成 / 跳过</dt><dd>{{ selected.completed }}</dd></div></dl>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ScreenProcess } from '@/api/dashboard'
const props = defineProps<{ processes: ScreenProcess[]; selectedId: number | null }>()
const emit = defineEmits<{ select: [id: number] }>()
const page = ref(0)
const visibleProcesses = computed(() => props.processes.slice(page.value * 5, (page.value + 1) * 5))
const selected = computed(() => props.processes.find(stage => stage.processId === props.selectedId))
watch([() => props.processes, () => props.selectedId], () => {
  const index = props.processes.findIndex(stage => stage.processId === props.selectedId)
  page.value = index >= 0 ? Math.floor(index / 5) : 0
}, { immediate: true })
function changePage(direction: number) {
  const target = props.processes[(page.value + direction) * 5]
  if (target) emit('select', target.processId)
}
</script>
