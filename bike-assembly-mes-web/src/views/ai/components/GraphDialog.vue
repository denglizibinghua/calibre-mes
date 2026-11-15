<template>
  <el-dialog
    :model-value="modelValue"
    width="min(900px, 94vw)"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @update:model-value="handleVisibleChange"
    @closed="shutdown"
  >
    <template #header="{ titleId }">
      <div class="kg-header">
        <h3 :id="titleId" class="kg-header__title">{{ displayTitle }}</h3>
        <span class="kg-header__counts">{{ nodes.length }} 个实体 · {{ edges.length }} 条关系</span>
      </div>
    </template>

    <el-alert
      v-if="chartError"
      class="kg-error"
      type="error"
      :title="chartError"
      :closable="false"
      show-icon
    />
    <div
      v-else-if="hasNodes"
      ref="chartElement"
      class="kg-canvas"
      role="img"
      :aria-label="`知识图谱：${nodes.length} 个实体，${edges.length} 条关系`"
    />
    <el-empty v-else class="kg-empty" :image-size="84" description="暂无可展示的知识图谱实体" />

    <template #footer>
      <div class="kg-footer">
        <span class="kg-footer__hint">滚轮缩放 · 拖拽节点 · 空白处拖动平移</span>
        <el-button @click="closeDialog">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { KnowledgeGraphEdge, KnowledgeGraphNode } from '@/api/ai'

const props = defineProps<{
  modelValue: boolean
  nodes: KnowledgeGraphNode[]
  edges: KnowledgeGraphEdge[]
  title?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const CATEGORY_COLORS = ['#3F8F79', '#A8894F', '#B8752E', '#A8454F', '#6F6A5F'] as const
const FALLBACK_TYPE = '其他'

const chartElement = ref<HTMLDivElement | null>(null)
const chartError = ref('')
let chart: echarts.ECharts | null = null
let resizeListening = false

const hasNodes = computed(() => props.nodes.length > 0)
const displayTitle = computed(() => props.title?.trim() || '知识图谱')

watch(
  () => props.modelValue,
  visible => {
    if (visible) void openChart()
    else shutdown()
  }
)

watch(
  () => [props.nodes, props.edges],
  async () => {
    if (!props.modelValue) return
    await nextTick()
    if (!props.modelValue) return
    renderChart()
  }
)

onMounted(() => {
  if (props.modelValue) void openChart()
})

onBeforeUnmount(shutdown)

async function openChart() {
  chartError.value = ''
  await nextTick()
  if (!props.modelValue) return
  renderChart()
  startResizeListener()
}

function renderChart() {
  const element = chartElement.value
  if (!element) {
    disposeChart()
    return
  }
  try {
    if (chart && chart.getDom() !== element) disposeChart()
    chart = chart ?? echarts.getInstanceByDom(element) ?? echarts.init(element)

    const categories = collectTypes()
    const categoryIndex = new Map<string, number>()
    categories.forEach((name, index) => categoryIndex.set(name, index))
    const degrees = new Map<string, number>()
    for (const edge of props.edges) {
      degrees.set(edge.source, (degrees.get(edge.source) ?? 0) + 1)
      degrees.set(edge.target, (degrees.get(edge.target) ?? 0) + 1)
    }
    const names = new Map<string, string>()
    for (const node of props.nodes) names.set(node.id, node.name)

    chart.setOption({
      animationDuration: 400,
      tooltip: {
        trigger: 'item',
        confine: true,
        backgroundColor: '#fff',
        borderColor: 'var(--lux-line)',
        textStyle: { color: 'var(--lux-text-strong)', fontSize: 12, lineHeight: 18 },
        extraCssText: 'box-shadow: 0 8px 24px rgb(20 40 70 / 12%); border-radius: 9px;',
        formatter: formatTooltip
      },
      legend: {
        data: categories,
        type: 'scroll',
        bottom: 2,
        left: 'center',
        icon: 'circle',
        itemWidth: 9,
        itemHeight: 9,
        itemGap: 14,
        textStyle: { color: 'var(--lux-text-muted)', fontSize: 12 }
      },
      series: [
        {
          type: 'graph',
          layout: 'force',
          roam: true,
          draggable: true,
          top: 18,
          bottom: 48,
          left: 26,
          right: 32,
          data: props.nodes.map(node => {
            const type = normalizeType(node.type)
            const category = categoryIndex.get(type) ?? 0
            const linkCount = degrees.get(node.id) ?? 0
            return {
              id: node.id,
              name: node.name,
              category,
              symbolSize: Math.min(48, 26 + linkCount * 3),
              nodeType: type,
              source: node.source,
              label: {
                show: true,
                position: 'right',
                distance: 6,
                color: 'var(--lux-text-strong)',
                fontSize: 11
              },
              itemStyle: {
                color: CATEGORY_COLORS[category % CATEGORY_COLORS.length]
              }
            }
          }),
          categories: categories.map((name, index) => ({
            name,
            itemStyle: {
              color: CATEGORY_COLORS[index % CATEGORY_COLORS.length],
              borderColor: '#fff',
              borderWidth: 1.5
            }
          })),
          links: props.edges.map(edge => ({
            source: edge.source,
            target: edge.target,
            name: edge.type,
            type: edge.type,
            documentSource: edge.documentSource,
            sourceName: names.get(edge.source) ?? edge.source,
            targetName: names.get(edge.target) ?? edge.target,
            lineStyle: { color: 'var(--lux-line-strong)', width: 1.4, curveness: 0.12 }
          })),
          force: {
            repulsion: 260,
            edgeLength: [80, 150],
            gravity: 0.09,
            layoutAnimation: true
          },
          edgeSymbol: ['none', 'arrow'],
          edgeSymbolSize: 8,
          edgeLabel: {
            show: true,
            color: 'var(--lux-text-faint)',
            fontSize: 10,
            formatter: formatEdgeLabel
          },
          lineStyle: { color: 'var(--lux-line-strong)', width: 1.4, curveness: 0.12, opacity: 0.9 },
          emphasis: {
            focus: 'adjacency',
            lineStyle: { width: 2.4, color: 'var(--lux-text-faint)' }
          },
          label: { show: true, position: 'right', color: 'var(--lux-text-strong)', fontSize: 11 }
        }
      ]
    }, true)
    chart.resize()
  } catch {
    chartError.value = '图谱渲染失败，请关闭弹窗后重试。'
    disposeChart()
  }
}

function collectTypes(): string[] {
  const types: string[] = []
  for (const node of props.nodes) {
    const type = normalizeType(node.type)
    if (!types.includes(type)) types.push(type)
  }
  return types
}

function normalizeType(type?: string): string {
  return type?.trim() || FALLBACK_TYPE
}

function formatEdgeLabel(params: echarts.DefaultLabelFormatterCallbackParams): string {
  return readText(toRecord(params.data), 'type') || params.name || ''
}

function formatTooltip(params: echarts.TooltipComponentFormatterCallbackParams): string {
  const item = Array.isArray(params) ? params[0] : params
  if (!item) return ''
  const data = toRecord(item.data)
  if (item.dataType === 'edge') {
    const from = readText(data, 'sourceName') || readText(data, 'source')
    const to = readText(data, 'targetName') || readText(data, 'target')
    const lines = [`<b>${escapeHtml(from)} → ${escapeHtml(to)}</b>`]
    const relation = readText(data, 'type') || item.name
    if (relation) lines.push(`关系：${escapeHtml(relation)}`)
    const documentSource = readText(data, 'documentSource')
    if (documentSource) lines.push(`文档来源：${escapeHtml(documentSource)}`)
    return lines.join('<br/>')
  }
  const name = item.name || readText(data, 'name')
  const lines = [`<b>${escapeHtml(name)}</b>`]
  const type = readText(data, 'nodeType') || readText(data, 'type')
  if (type) lines.push(`类型：${escapeHtml(type)}`)
  const source = readText(data, 'source')
  if (source) lines.push(`来源：${escapeHtml(source)}`)
  return lines.join('<br/>')
}

function toRecord(value: unknown): Record<string, unknown> {
  return typeof value === 'object' && value !== null ? value as Record<string, unknown> : {}
}

function readText(record: Record<string, unknown>, key: string): string {
  const value = record[key]
  return typeof value === 'string' ? value : ''
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function handleResize() {
  chart?.resize()
}

function startResizeListener() {
  if (resizeListening) return
  window.addEventListener('resize', handleResize)
  resizeListening = true
}

function stopResizeListener() {
  if (!resizeListening) return
  window.removeEventListener('resize', handleResize)
  resizeListening = false
}

function disposeChart() {
  chart?.dispose()
  chart = null
}

function shutdown() {
  stopResizeListener()
  disposeChart()
}

function handleVisibleChange(value: boolean) {
  emit('update:modelValue', value)
}

function closeDialog() {
  emit('update:modelValue', false)
}
</script>

<style scoped>
.kg-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  min-width: 0;
  padding-right: 26px;
}

.kg-header__title {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--mes-text-primary, var(--lux-text-strong));
  font-size: 17px;
  font-weight: 700;
  line-height: 1.4;
  letter-spacing: -0.1px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.kg-header__counts {
  flex: 0 0 auto;
  padding: 3px 10px;
  color: var(--mes-text-secondary, var(--lux-text-muted));
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  line-height: 1.6;
  white-space: nowrap;
  background: var(--mes-bg-soft, var(--lux-surface-soft));
  border: 1px solid var(--mes-border, var(--lux-line));
  border-radius: 999px;
}

.kg-canvas {
  width: 100%;
  height: 520px;
  overflow: hidden;
  background: var(--lux-surface-soft);
  border: 1px solid var(--mes-border-light, var(--lux-line-soft));
  border-radius: 14px;
}

.kg-empty {
  padding: 30px 0 22px;
}

.kg-error {
  border-radius: 10px;
}

.kg-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.kg-footer__hint {
  color: var(--mes-text-muted, var(--lux-text-faint));
  font-size: 12px;
  line-height: 1.5;
}

.kg-footer .el-button {
  margin-left: auto;
}

@media (max-width: 640px) {
  .kg-header {
    flex-wrap: wrap;
    gap: 8px;
  }

  .kg-footer {
    flex-wrap: wrap;
  }
}
</style>
