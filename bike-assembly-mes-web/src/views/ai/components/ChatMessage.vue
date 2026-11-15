<template>
  <article
    ref="rootElement"
    class="chat-message"
    :class="isUser ? 'is-user' : 'is-assistant'"
  >
    <div v-if="!isUser" class="assistant-avatar" aria-hidden="true">
      <el-icon :size="18"><MagicStick /></el-icon>
    </div>

    <div class="message-main">
      <template v-if="isUser">
        <div class="user-bubble">{{ message.content }}</div>
        <div class="time-row" :title="fullTimeLabel">{{ timeLabel }}</div>
      </template>

      <template v-else>
        <div v-if="showAnswerBubble" class="assistant-bubble">
          <div
            v-if="message.status === 'pending' && !message.content"
            class="typing"
            role="status"
            aria-label="AI 正在生成回答"
          >
            <span class="typing-dot" />
            <span class="typing-dot" />
            <span class="typing-dot" />
          </div>
          <div v-else class="answer-body">
            <AnswerMarkdown
              v-if="message.content"
              class="answer-markdown"
              :content="message.content"
              :citations="message.citations"
              @cite="focusCitation"
            />
            <p v-else-if="message.status === 'done'" class="empty-answer">本次未返回内容。</p>
            <span
              v-if="active && message.status === 'streaming'"
              class="stream-caret"
              aria-hidden="true"
            />
          </div>
        </div>

        <div v-if="hasMetadata" class="meta-row">
          <span v-if="message.model" class="meta-item">模型 {{ message.model }}</span>
          <span v-if="message.dataTimeRange" class="meta-item">数据时间 {{ message.dataTimeRange }}</span>
          <span v-if="message.dataScope" class="meta-item">数据范围 {{ message.dataScope }}</span>
          <el-tag v-if="message.fallback" type="warning" size="small" effect="light">本地回退</el-tag>
          <span v-if="elapsedLabel" class="meta-item">耗时 {{ elapsedLabel }}</span>
        </div>

        <section v-if="dataSources.length" class="evidence-block">
          <div class="block-title">MES 数据依据</div>
          <div class="source-chips">
            <span
              v-for="(source, position) in dataSources"
              :key="`${message.id}-data-${position}`"
              class="source-chip"
            >{{ source }}</span>
          </div>
        </section>

        <section v-if="message.citations.length" class="evidence-block">
          <div class="block-title">知识库依据</div>
          <ul class="citation-list">
            <li
              v-for="(citation, position) in message.citations"
              :id="`citation-${message.id}-${citation.index}`"
              :key="`${message.id}-citation-${position}`"
              class="citation-card"
              :data-citation-index="citation.index"
            >
              <span class="citation-index">{{ citation.index }}</span>
              <div class="citation-body">
                <div class="citation-file">{{ citation.fileName }}</div>
                <div v-if="citation.title" class="citation-title">{{ citation.title }}</div>
                <div v-if="citation.chunk != null || citation.updatedAt" class="citation-meta">
                  <span v-if="citation.chunk != null">chunk {{ citation.chunk }}</span>
                  <span v-if="citation.updatedAt">更新于 {{ formatDateTime(citation.updatedAt) }}</span>
                </div>
              </div>
            </li>
          </ul>
        </section>

        <div v-if="message.status === 'error'" class="error-bar" role="alert">
          <span class="error-text">{{ message.error || '回答生成失败，请稍后重试。' }}</span>
          <el-button
            v-if="canRetry"
            link
            type="danger"
            size="small"
            class="error-retry"
            aria-label="重新生成回答"
            @click="emitRetry"
          >
            重新生成
          </el-button>
        </div>

        <div v-if="showActions" class="action-row">
          <el-button
            v-if="canCopy"
            link
            size="small"
            aria-label="复制回答"
            @click="emit('copy', message)"
          >
            <el-icon><CopyDocument /></el-icon>
            复制回答
          </el-button>
          <el-button
            v-if="canRetry && message.status !== 'error'"
            link
            size="small"
            aria-label="重新生成回答"
            @click="emitRetry"
          >
            <el-icon><Refresh /></el-icon>
            重新生成
          </el-button>
          <el-button
            v-if="canGraph"
            link
            size="small"
            aria-label="查看知识图谱"
            @click="emit('open-graph', message)"
          >
            <el-icon><Share /></el-icon>
            查看图谱
          </el-button>
        </div>

        <div class="time-row" :title="fullTimeLabel">{{ timeLabel }}</div>
      </template>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { CopyDocument, MagicStick, Refresh, Share } from '@element-plus/icons-vue'
import AnswerMarkdown from '@/views/ai/components/AnswerMarkdown.vue'
import { formatDateTime, splitSources } from '@/views/ai/citation'
import type { ChatMessage } from '@/views/ai/types'

const props = withDefaults(defineProps<{
  message: ChatMessage
  active?: boolean
}>(), {
  active: false
})

const emit = defineEmits<{
  retry: [messageId: string]
  copy: [message: ChatMessage]
  'open-graph': [message: ChatMessage]
}>()

const rootElement = ref<HTMLElement | null>(null)
let highlightTimer: number | undefined

const isUser = computed(() => props.message.role === 'user')
const dataSources = computed(() => splitSources(props.message.sources).dataSources)
const hasMetadata = computed(() => !isUser.value && Boolean(
  props.message.model
  || props.message.dataTimeRange
  || props.message.dataScope
  || props.message.fallback
  || props.message.elapsedMs != null
))
const showAnswerBubble = computed(() => Boolean(props.message.content)
  || props.message.status === 'pending'
  || props.message.status === 'streaming'
  || props.message.status === 'done')
const canCopy = computed(() => !isUser.value
  && props.message.status !== 'pending'
  && Boolean(props.message.content.trim()))
const canRetry = computed(() => !isUser.value && Boolean(props.message.question))
const canGraph = computed(() => !isUser.value && props.message.graphNodes.length > 0)
const showActions = computed(() => canCopy.value || canRetry.value || canGraph.value)

const elapsedLabel = computed(() => {
  const elapsed = props.message.elapsedMs
  if (elapsed == null || !Number.isFinite(elapsed) || elapsed < 0) return ''
  if (elapsed < 1000) return `${Math.round(elapsed)} ms`
  return `${(elapsed / 1000).toFixed(1)} s`
})

const timeLabel = computed(() => {
  const date = new Date(props.message.createdAt)
  if (Number.isNaN(date.getTime())) return ''
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
})

const fullTimeLabel = computed(() => {
  const date = new Date(props.message.createdAt)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
})

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

function emitRetry() {
  emit('retry', props.message.id)
}

function focusCitation(index: number) {
  const container = rootElement.value
  if (!container) return

  const cards = Array.from(container.querySelectorAll<HTMLElement>('.citation-card'))
  const target = cards.find(card => card.dataset.citationIndex === String(index))
  if (!target) return

  if (highlightTimer != null) window.clearTimeout(highlightTimer)
  cards.forEach(card => card.classList.remove('is-highlighted'))
  target.classList.add('is-highlighted')
  target.scrollIntoView({ behavior: 'smooth', block: 'nearest' })

  highlightTimer = window.setTimeout(() => {
    target.classList.remove('is-highlighted')
    highlightTimer = undefined
  }, 1200)
}

onBeforeUnmount(() => {
  if (highlightTimer != null) window.clearTimeout(highlightTimer)
})
</script>

<style scoped>
.chat-message {
  display: flex;
  gap: 10px;
  width: 100%;
  min-width: 0;
  animation: chat-message-in 0.2s ease both;
}

.chat-message.is-user {
  justify-content: flex-end;
}

.assistant-avatar {
  display: flex;
  flex: 0 0 34px;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  margin-top: 2px;
  color: #fff;
  background: linear-gradient(135deg, var(--mes-primary, var(--lux-gold-700)), var(--lux-gold-500));
  border-radius: 50%;
  box-shadow: 0 4px 10px rgb(198 169 110 / 22%);
}

.message-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  max-width: min(100%, 860px);
}

.is-user .message-main {
  align-items: flex-end;
  max-width: min(82%, 720px);
}

.is-assistant .message-main {
  flex: 1;
  align-items: flex-start;
}

.user-bubble {
  max-width: 100%;
  padding: 10px 14px;
  overflow-wrap: anywhere;
  color: #fff;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--mes-primary, var(--lux-gold-700));
  border-radius: 16px 16px 5px 16px;
  box-shadow: 0 6px 16px rgb(198 169 110 / 15%);
}

.assistant-bubble {
  box-sizing: border-box;
  max-width: 100%;
  min-width: 0;
  padding: 12px 15px;
  overflow: hidden;
  background: var(--mes-bg-card, #fff);
  border: 1px solid var(--mes-border, var(--lux-line));
  border-radius: 5px 16px 16px;
  box-shadow: var(--mes-shadow-xs, 0 2px 8px rgb(20 40 70 / 4%));
}

.answer-body {
  display: flex;
  min-width: 0;
  align-items: flex-end;
  gap: 3px;
}

.answer-markdown {
  flex: 1 1 auto;
  min-width: 0;
}

.empty-answer {
  margin: 0;
  color: var(--mes-text-muted, var(--lux-text-faint));
  font-size: 13px;
  line-height: 1.6;
}

.typing {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 2px;
}

.typing-dot {
  width: 6px;
  height: 6px;
  background: var(--lux-text-faint);
  border-radius: 50%;
  animation: typing-pulse 1.1s ease-in-out infinite;
}

.typing-dot:nth-child(2) {
  animation-delay: 0.15s;
}

.typing-dot:nth-child(3) {
  animation-delay: 0.3s;
}

.stream-caret {
  display: inline-block;
  flex: 0 0 2px;
  width: 2px;
  height: 1.05em;
  min-height: 15px;
  margin-bottom: 4px;
  background: var(--mes-primary, var(--lux-gold-700));
  border-radius: 1px;
  animation: caret-blink 1s steps(1) infinite;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 12px;
  margin-top: 8px;
  color: var(--mes-text-muted, var(--lux-text-faint));
  font-size: 12px;
  line-height: 1.5;
}

.meta-item {
  min-width: 0;
  overflow-wrap: anywhere;
}

.evidence-block {
  width: 100%;
  min-width: 0;
  margin-top: 10px;
}

.block-title {
  margin-bottom: 6px;
  color: var(--mes-text-secondary, var(--lux-text-muted));
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.2px;
}

.source-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.source-chip {
  display: inline-block;
  max-width: 100%;
  padding: 2px 9px;
  overflow-wrap: anywhere;
  color: var(--mes-primary-hover, var(--lux-gold-700));
  font-size: 12px;
  line-height: 1.6;
  word-break: break-word;
  background: var(--mes-primary-soft, #f7f1e4);
  border: 1px solid var(--lux-line-strong);
  border-radius: 999px;
}

.citation-list {
  display: grid;
  gap: 7px;
  min-width: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.citation-card {
  display: flex;
  gap: 9px;
  min-width: 0;
  padding: 9px 11px;
  background: var(--mes-bg-soft, var(--lux-surface-soft));
  border: 1px solid var(--mes-border, var(--lux-line));
  border-radius: 10px;
  transition: background-color 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.citation-card.is-highlighted {
  background: var(--mes-primary-soft, #f7f1e4);
  border-color: var(--mes-primary, var(--lux-gold-700));
  box-shadow: 0 0 0 3px rgb(198 169 110 / 12%);
}

.citation-index {
  display: inline-flex;
  flex: 0 0 20px;
  width: 20px;
  height: 20px;
  align-items: center;
  justify-content: center;
  margin-top: 1px;
  color: var(--mes-primary-hover, var(--lux-gold-700));
  font-size: 11px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  background: var(--mes-primary-soft, #f7f1e4);
  border: 1px solid var(--lux-line-strong);
  border-radius: 50%;
}

.citation-body {
  flex: 1 1 auto;
  min-width: 0;
}

.citation-file {
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--mes-text-primary, var(--lux-text-strong));
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
  word-break: break-word;
}

.citation-title {
  margin-top: 2px;
  overflow-wrap: anywhere;
  color: var(--mes-text-secondary, var(--lux-text-muted));
  font-size: 12px;
  line-height: 1.5;
  word-break: break-word;
}

.citation-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 3px 12px;
  margin-top: 4px;
  color: var(--mes-text-muted, var(--lux-text-faint));
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  line-height: 1.5;
}

.error-bar {
  display: flex;
  width: 100%;
  box-sizing: border-box;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-top: 10px;
  padding: 8px 10px;
  color: var(--lux-wine-600);
  font-size: 12px;
  line-height: 1.6;
  background: var(--lux-wine-50);
  border: 1px solid var(--lux-wine-line);
  border-radius: 9px;
}

.error-text {
  min-width: 0;
  overflow-wrap: anywhere;
}

.error-retry {
  flex: 0 0 auto;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px;
  margin-top: 6px;
}

.action-row :deep(.el-button + .el-button) {
  margin-left: 4px;
}

.action-row :deep(.el-button) {
  font-size: 12px;
}

.time-row {
  margin-top: 5px;
  color: var(--mes-text-muted, var(--lux-text-faint));
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  line-height: 1.4;
}

@keyframes chat-message-in {
  from {
    opacity: 0;
    transform: translateY(4px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes typing-pulse {
  0%,
  65%,
  100% {
    opacity: 0.35;
  }

  30% {
    opacity: 1;
  }
}

@keyframes caret-blink {
  0%,
  45% {
    opacity: 1;
  }

  50%,
  100% {
    opacity: 0;
  }
}

@media (max-width: 640px) {
  .chat-message {
    gap: 8px;
  }

  .assistant-bubble {
    padding: 11px 12px;
  }

  .user-bubble {
    padding: 9px 12px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .chat-message {
    animation: none;
  }

  .typing-dot {
    animation: none;
    opacity: 0.7;
  }

  .stream-caret {
    animation: none;
  }

  .citation-card {
    transition: none;
  }
}
</style>
