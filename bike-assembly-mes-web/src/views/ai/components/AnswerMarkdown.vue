<template>
  <div v-if="html" class="answer-markdown">
    <div class="markdown-body" v-html="html" @click="handleClick" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '@/views/ai/markdown'
import type { AiCitation } from '@/views/ai/types'

const props = withDefaults(defineProps<{
  content: string
  citations?: AiCitation[]
}>(), {
  citations: () => []
})

const emit = defineEmits<{
  cite: [index: number]
}>()

const html = computed(() => renderMarkdown(props.content))

function handleClick(event: MouseEvent) {
  const target = event.target
  if (!(target instanceof Element)) return
  const chip = target.closest<HTMLElement>('[data-citation-index]')
  if (!chip) return
  const index = Number(chip.dataset.citationIndex)
  if (Number.isFinite(index)) emit('cite', index)
}
</script>

<style scoped>
.answer-markdown {
  min-width: 0;
  max-width: 100%;
  color: var(--mes-text-primary, var(--lux-text-strong));
  font-size: 14px;
}

.markdown-body {
  min-width: 0;
  font-family: inherit;
  line-height: 1.75;
  overflow-wrap: break-word;
  word-break: break-word;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  color: var(--mes-text-primary, var(--lux-text-strong));
  font-weight: 650;
  line-height: 1.4;
}

.markdown-body :deep(h1) {
  margin: 20px 0 10px;
  padding-bottom: 8px;
  font-size: 20px;
  letter-spacing: -0.2px;
  border-bottom: 1px solid var(--mes-border-light, var(--lux-line-soft));
}

.markdown-body :deep(h2) {
  margin: 18px 0 9px;
  padding-bottom: 7px;
  font-size: 18px;
  border-bottom: 1px solid var(--mes-border-light, var(--lux-line-soft));
}

.markdown-body :deep(h3) {
  margin: 16px 0 7px;
  font-size: 15.5px;
}

.markdown-body :deep(h4) {
  margin: 14px 0 6px;
  font-size: 14px;
}

:deep(.markdown-body > :first-child) {
  margin-top: 0;
}

:deep(.markdown-body > :last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(p) {
  margin: 9px 0;
  line-height: 1.75;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 8px 0;
  padding-left: 1.45em;
}

.markdown-body :deep(ul) {
  list-style: disc;
}

.markdown-body :deep(ol) {
  list-style: decimal;
}

.markdown-body :deep(li) {
  margin: 3px 0;
  line-height: 1.7;
}

.markdown-body :deep(li > ul),
.markdown-body :deep(li > ol) {
  margin: 3px 0;
}

.markdown-body :deep(a) {
  color: var(--mes-primary, var(--lux-gold-700));
  font-weight: 500;
  text-decoration: none;
  border-bottom: 1px solid rgb(198 169 110 / 28%);
  transition: color var(--mes-transition-fast, 0.16s ease), border-color var(--mes-transition-fast, 0.16s ease);
}

.markdown-body :deep(a:hover) {
  color: var(--mes-primary-hover, var(--lux-gold-700));
  border-bottom-color: currentcolor;
}

.markdown-body :deep(strong) {
  color: var(--mes-text-primary, var(--lux-text-strong));
  font-weight: 650;
}

.markdown-body :deep(hr) {
  margin: 18px 0;
  border: 0;
  border-top: 1px solid var(--mes-border-light, var(--lux-line-soft));
}

.markdown-body :deep(blockquote) {
  margin: 12px 0;
  padding: 8px 14px;
  color: var(--mes-text-secondary, var(--lux-text-muted));
  background: var(--mes-bg-soft, var(--lux-surface-soft));
  border-left: 3px solid var(--lux-gold-500);
  border-radius: 0 9px 9px 0;
}

.markdown-body :deep(blockquote > :first-child) {
  margin-top: 0;
}

.markdown-body :deep(blockquote > :last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(code) {
  padding: 1px 5px;
  color: var(--lux-text-strong);
  font-family: ui-monospace, SFMono-Regular, 'SF Mono', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 0.88em;
  word-break: break-word;
  background: var(--lux-surface-soft);
  border: 1px solid var(--mes-border-light, var(--lux-line-soft));
  border-radius: 5px;
}

.markdown-body :deep(pre) {
  margin: 12px 0;
  padding: 12px 14px;
  overflow-x: auto;
  background: var(--mes-bg-soft, var(--lux-surface-soft));
  border: 1px solid var(--mes-border-light, var(--lux-line-soft));
  border-radius: 9px;
}

.markdown-body :deep(pre code) {
  padding: 0;
  font-size: 12.5px;
  line-height: 1.65;
  background: transparent;
  border: 0;
  border-radius: 0;
  white-space: pre;
}

.markdown-body :deep(.table-scroll) {
  max-width: 100%;
  margin: 12px 0;
  overflow-x: auto;
  border: 1px solid var(--mes-border, var(--lux-line));
  border-radius: 9px;
}

.markdown-body :deep(table) {
  width: 100%;
  min-width: 420px;
  border-collapse: collapse;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 7px 11px;
  line-height: 1.55;
  text-align: left;
  vertical-align: top;
  border-bottom: 1px solid var(--mes-border-light, var(--lux-line-soft));
}

.markdown-body :deep(th) {
  color: var(--lux-text);
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.1px;
  background: var(--lux-surface-soft);
  white-space: nowrap;
}

.markdown-body :deep(th:not(:last-child)),
.markdown-body :deep(td:not(:last-child)) {
  border-right: 1px solid var(--mes-border-light, var(--lux-line-soft));
}

.markdown-body :deep(tr:last-child td) {
  border-bottom: 0;
}

.markdown-body :deep(tbody tr:hover td) {
  background: var(--lux-surface-soft);
}

.markdown-body :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 9px;
}

.markdown-body :deep(.citation-chip) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 20px;
  margin: 0 2px;
  padding: 0 7px;
  color: var(--mes-primary-hover, var(--lux-gold-700));
  font-family: inherit;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
  vertical-align: 1px;
  background: var(--mes-primary-soft, #f7f1e4);
  border: 1px solid var(--lux-line-strong);
  border-radius: 999px;
  cursor: pointer;
  transition: background-color var(--mes-transition-fast, 0.16s ease), border-color var(--mes-transition-fast, 0.16s ease), box-shadow var(--mes-transition-fast, 0.16s ease);
}

.markdown-body :deep(.citation-chip:hover) {
  background: #eadcc0;
  border-color: var(--lux-gold-500);
  box-shadow: 0 2px 6px rgb(198 169 110 / 14%);
}

.markdown-body :deep(.citation-chip:active) {
  background: #eadcc0;
}

.markdown-body :deep(.citation-chip:focus-visible) {
  outline: 2px solid rgb(198 169 110 / 45%);
  outline-offset: 2px;
}

.markdown-body :deep(input[type='checkbox']) {
  margin-right: 4px;
  vertical-align: -1px;
}
</style>
