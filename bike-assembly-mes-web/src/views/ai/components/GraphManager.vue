<template>
  <el-drawer
    :model-value="modelValue"
    size="min(1080px, 96vw)"
    direction="rtl"
    append-to-body
    :close-on-click-modal="false"
    @update:model-value="handleVisibleChange"
    @opened="handleOpened"
    @closed="shutdown"
  >
    <template #header="{ titleId }">
      <div class="gm-header">
        <h3 :id="titleId" class="gm-header__title">知识图谱管理</h3>
        <span class="gm-header__meta">
          {{ overview ? `${overview.nodeCount} 个实体 · ${overview.edgeCount} 条关系` : '正在读取概览' }}
        </span>
      </div>
    </template>

    <div class="gm-body">
      <section
        v-loading="overviewLoading"
        element-loading-text="读取图谱概览…"
        class="stat-grid gm-stats"
        aria-label="图谱概览"
      >
        <div class="stat-card">
          <div class="stat-card-label">实体总数</div>
          <div class="stat-card-value">{{ overview ? overview.nodeCount : '—' }}</div>
          <div class="stat-card-hint">{{ overview ? `${overview.nodeTypes.length} 类实体` : '—' }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-card-label">关系总数</div>
          <div class="stat-card-value">{{ overview ? overview.edgeCount : '—' }}</div>
          <div class="stat-card-hint">{{ overview ? `${overview.relationTypes.length} 类关系` : '—' }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-card-label">公共数据</div>
          <div class="stat-card-value">{{ overview ? overview.publicCount : '—' }}</div>
          <div class="stat-card-hint">全部用户可见</div>
        </div>
        <div class="stat-card">
          <div class="stat-card-label">私有数据</div>
          <div class="stat-card-value">{{ overview ? overview.privateCount : '—' }}</div>
          <div class="stat-card-hint">仅当前账号可见</div>
        </div>
      </section>

      <section class="gm-filters" aria-label="实体与关系筛选">
        <el-select
          v-model="entityType"
          class="gm-filter"
          clearable
          placeholder="实体类型"
          aria-label="实体类型"
          @change="handleEntityFilterChange"
        >
          <el-option
            v-for="item in ENTITY_TYPE_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select
          v-model="relationType"
          class="gm-filter"
          clearable
          placeholder="关系类型"
          aria-label="关系类型"
          @change="handleRelationFilterChange"
        >
          <el-option
            v-for="item in RELATION_TYPE_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-input
          v-model="keyword"
          class="gm-keyword"
          clearable
          placeholder="搜索实体名称"
          aria-label="搜索实体名称"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <div class="gm-filter-actions">
          <el-button type="primary" :loading="nodesLoading" @click="handleSearch">
            <el-icon v-if="!nodesLoading"><Search /></el-icon>
            查询
          </el-button>
          <el-button :disabled="nodesLoading" @click="resetFilters">重置</el-button>
        </div>
      </section>

      <section class="gm-section" aria-label="图谱实体">
        <div class="gm-section-head">
          <div class="gm-section-title">
            <h4>图谱实体</h4>
            <p>点击实体行加载其一跳关系</p>
          </div>
          <span class="gm-count">共 {{ nodeTotal }} 个实体</span>
        </div>

        <div class="gm-table-wrap">
          <el-table
            v-loading="nodesLoading"
            element-loading-text="加载实体…"
            :data="nodes"
            size="small"
            row-key="id"
            highlight-current-row
            :max-height="300"
            @row-click="handleNodeClick"
          >
            <el-table-column prop="name" label="实体名称" min-width="180" show-overflow-tooltip />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ entityTypeLabel(row.type) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="范围" width="78">
              <template #default="{ row }">
                <el-tag size="small" :type="row.scope === 'PUBLIC' ? 'success' : 'info'">
                  {{ scopeLabel(row.scope) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="关联关系" width="92" align="right">
              <template #default="{ row }">
                <span class="gm-num">{{ row.relationCount }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="76" align="right" fixed="right">
              <template #default="{ row }">
                <el-button
                  link
                  type="danger"
                  :disabled="deleting"
                  @click.stop="removeNode(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty :image-size="72" :description="nodesEmptyText" />
            </template>
          </el-table>
        </div>

        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="nodePage"
            v-model:page-size="nodeSize"
            :total="nodeTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="loadNodes"
            @size-change="handleNodeSizeChange"
          />
        </div>
      </section>

      <section class="gm-section" aria-label="实体关系">
        <div class="gm-section-head">
          <div class="gm-section-title">
            <h4>实体关系</h4>
            <p>{{ selectedNode ? `当前实体：${selectedNode.name}` : '选择上方实体查看其一跳关系' }}</p>
          </div>
          <span class="gm-count">共 {{ edgeTotal }} 条关系</span>
        </div>

        <div class="gm-table-wrap">
          <el-table
            v-loading="edgesLoading"
            element-loading-text="加载关系…"
            :data="edges"
            size="small"
            :max-height="300"
          >
            <el-table-column prop="fromName" label="起点" min-width="140" show-overflow-tooltip />
            <el-table-column label="关系" width="116">
              <template #default="{ row }">
                <el-tag size="small">{{ relationTypeLabel(row.type) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="toName" label="终点" min-width="140" show-overflow-tooltip />
            <el-table-column prop="source" label="来源" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <span>{{ row.source || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="范围" width="72">
              <template #default="{ row }">
                <el-tag size="small" :type="row.scope === 'PUBLIC' ? 'success' : 'info'">
                  {{ scopeLabel(row.scope) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="76" align="right" fixed="right">
              <template #default="{ row }">
                <el-button
                  link
                  type="danger"
                  :disabled="deleting"
                  @click.stop="removeEdge(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty :image-size="72" :description="edgesEmptyText" />
            </template>
          </el-table>
        </div>

        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="edgePage"
            v-model:page-size="edgeSize"
            :total="edgeTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="loadEdges"
            @size-change="handleEdgeSizeChange"
          />
        </div>
      </section>

      <section class="gm-section" aria-label="一跳关系图">
        <div class="gm-section-head">
          <div class="gm-section-title">
            <h4>一跳关系图</h4>
            <p>{{ selectedNode ? `以「${selectedNode.name}」为中心` : '选择实体后展示一跳网络' }}</p>
          </div>
          <span class="gm-count">
            {{ previewData.nodes.length }} 个实体 · {{ previewData.links.length }} 条关系
          </span>
        </div>

        <div v-if="selectedNode" class="gm-preview">
          <div
            ref="chartElement"
            class="gm-canvas"
            role="img"
            :aria-label="previewAriaLabel"
          />
          <p class="gm-preview-hint">预览基于当前关系页数据 · 滚轮缩放 · 拖拽节点</p>
        </div>
        <el-empty
          v-else
          class="gm-preview-empty"
          :image-size="72"
          description="点击上方实体查看一跳关系图"
        />
      </section>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  deleteAiGraphEdge,
  deleteAiGraphNode,
  getAiGraphOverview,
  listAiGraphEdges,
  listAiGraphNodes
} from '@/api/ai'
import type { AiGraphEdge, AiGraphNode, AiGraphOverview } from '@/views/ai/types'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const ENTITY_TYPE_LABELS: Record<string, string> = {
  PROCESS: '工序',
  ITEM: '物料',
  EQUIPMENT: '设备',
  DEFECT: '缺陷',
  PARAMETER: '参数',
  DOCUMENT: '文档'
}

const RELATION_TYPE_LABELS: Record<string, string> = {
  USES: '使用',
  HAS_STEP: '包含工序',
  REQUIRES: '依赖',
  AFFECTS: '影响',
  MEASURED_BY: '由…测量',
  RELATED_TO: '关联'
}

const ENTITY_TYPE_OPTIONS = Object.entries(ENTITY_TYPE_LABELS).map(([value, label]) => ({ value, label }))
const RELATION_TYPE_OPTIONS = Object.entries(RELATION_TYPE_LABELS).map(([value, label]) => ({ value, label }))

const CATEGORY_COLORS = ['#3F8F79', '#A8894F', '#B8752E', '#A8454F', '#6F6A5F'] as const
const FALLBACK_TYPE = '其他'

const overview = ref<AiGraphOverview | null>(null)
const overviewLoading = ref(false)

const nodes = ref<AiGraphNode[]>([])
const nodeTotal = ref(0)
const nodePage = ref(1)
const nodeSize = ref(10)
const nodesLoading = ref(false)

const edges = ref<AiGraphEdge[]>([])
const edgeTotal = ref(0)
const edgePage = ref(1)
const edgeSize = ref(10)
const edgesLoading = ref(false)

const entityType = ref('')
const relationType = ref('')
const keyword = ref('')
const selectedNode = ref<AiGraphNode | null>(null)
const deleting = ref(false)

const chartElement = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let resizeListening = false
let nodeRequest = 0
let edgeRequest = 0

const previewData = computed(() => buildPreview())
const previewAriaLabel = computed(() => (
  `一跳关系图：${previewData.value.nodes.length} 个实体，${previewData.value.links.length} 条关系`
))
const nodesEmptyText = computed(() => (
  entityType.value || keyword.value.trim() ? '没有匹配的图谱实体' : '图谱中暂无实体'
))
const edgesEmptyText = computed(() => (
  selectedNode.value ? '该实体暂无关联关系' : '选择上方实体查看其一跳关系'
))

watch(() => props.modelValue, visible => {
  if (visible) void openPanel()
  else shutdown()
})

watch([selectedNode, edges], async () => {
  await nextTick()
  renderPreview()
})

onMounted(() => {
  if (props.modelValue) void openPanel()
})

onBeforeUnmount(shutdown)

async function openPanel() {
  resetSelection()
  edgePage.value = 1
  nodePage.value = 1
  await Promise.all([loadOverview(), loadNodes()])
  startResizeListener()
  await nextTick()
  renderPreview()
}

async function loadOverview() {
  overviewLoading.value = true
  try {
    overview.value = await getAiGraphOverview()
  } catch {
    ElMessage.error('图谱概览加载失败')
  } finally {
    overviewLoading.value = false
  }
}

async function loadNodes() {
  const requestId = ++nodeRequest
  nodesLoading.value = true
  try {
    const result = await listAiGraphNodes({
      type: entityType.value || undefined,
      keyword: keyword.value.trim() || undefined,
      page: nodePage.value,
      size: nodeSize.value
    })
    if (requestId !== nodeRequest) return
    nodes.value = result.records ?? []
    nodeTotal.value = result.total ?? 0
    if (nodeTotal.value === 0) nodePage.value = 1
  } catch {
    if (requestId === nodeRequest) ElMessage.error('实体列表加载失败')
  } finally {
    if (requestId === nodeRequest) nodesLoading.value = false
  }
}

async function loadEdges() {
  const node = selectedNode.value
  if (!node) {
    edgeRequest += 1
    edges.value = []
    edgeTotal.value = 0
    edgesLoading.value = false
    disposeChart()
    return
  }
  const requestId = ++edgeRequest
  edgesLoading.value = true
  try {
    const result = await listAiGraphEdges({
      nodeId: node.id,
      type: relationType.value || undefined,
      page: edgePage.value,
      size: edgeSize.value
    })
    if (requestId !== edgeRequest) return
    edges.value = result.records ?? []
    edgeTotal.value = result.total ?? 0
    if (edgeTotal.value === 0) edgePage.value = 1
  } catch {
    if (requestId === edgeRequest) ElMessage.error('关系列表加载失败')
  } finally {
    if (requestId === edgeRequest) {
      edgesLoading.value = false
      await nextTick()
      renderPreview()
    }
  }
}

function handleEntityFilterChange() {
  resetSelection()
  nodePage.value = 1
  void loadNodes()
}

function handleRelationFilterChange() {
  edgePage.value = 1
  if (selectedNode.value) void loadEdges()
}

function handleSearch() {
  resetSelection()
  nodePage.value = 1
  void loadNodes()
}

function resetFilters() {
  entityType.value = ''
  relationType.value = ''
  keyword.value = ''
  resetSelection()
  nodePage.value = 1
  edgePage.value = 1
  void loadNodes()
}

function resetSelection() {
  edgeRequest += 1
  selectedNode.value = null
  edges.value = []
  edgeTotal.value = 0
  edgesLoading.value = false
  disposeChart()
}

function handleNodeClick(row: AiGraphNode) {
  selectedNode.value = row
  edgePage.value = 1
  void loadEdges()
}

function handleNodeSizeChange(size: number) {
  nodeSize.value = size
  nodePage.value = 1
  void loadNodes()
}

function handleEdgeSizeChange(size: number) {
  edgeSize.value = size
  edgePage.value = 1
  void loadEdges()
}

async function removeNode(row: AiGraphNode) {
  if (deleting.value) return
  try {
    await ElMessageBox.confirm(
      `删除实体「${row.name}」及其全部关联关系？删除后不可恢复。`,
      '删除实体',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }
  deleting.value = true
  try {
    await deleteAiGraphNode(row.id)
    ElMessage.success('实体已删除')
    if (selectedNode.value?.id === row.id) resetSelection()
    await loadOverview()
    await loadNodes()
    if (nodes.value.length === 0 && nodePage.value > 1 && nodeTotal.value > 0) {
      nodePage.value -= 1
      await loadNodes()
    }
    if (selectedNode.value) await loadEdges()
  } catch {
    ElMessage.error('删除实体失败')
  } finally {
    deleting.value = false
  }
}

async function removeEdge(edge: AiGraphEdge) {
  if (deleting.value) return
  try {
    await ElMessageBox.confirm(
      `删除关系「${edge.fromName} → ${edge.toName}（${relationTypeLabel(edge.type)}）」？`,
      '删除关系',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }
  deleting.value = true
  try {
    await deleteAiGraphEdge({
      fromId: edge.fromId,
      toId: edge.toId,
      type: edge.type
    })
    ElMessage.success('关系已删除')
    await loadOverview()
    await loadNodes()
    await loadEdges()
    if (edges.value.length === 0 && edgePage.value > 1 && edgeTotal.value > 0) {
      edgePage.value -= 1
      await loadEdges()
    }
  } catch {
    ElMessage.error('删除关系失败')
  } finally {
    deleting.value = false
  }
}

interface PreviewNode {
  id: string
  name: string
  type: string
}

interface PreviewLink {
  source: string
  target: string
  type: string
}

function buildPreview(): { nodes: PreviewNode[]; links: PreviewLink[] } {
  const selected = selectedNode.value
  if (!selected) return { nodes: [], links: [] }
  const nodeMap = new Map<string, PreviewNode>()
  nodeMap.set(selected.id, { id: selected.id, name: selected.name, type: selected.type })
  const links: PreviewLink[] = []
  for (const edge of edges.value) {
    if (!nodeMap.has(edge.fromId)) {
      nodeMap.set(edge.fromId, { id: edge.fromId, name: edge.fromName, type: edge.fromType })
    }
    if (!nodeMap.has(edge.toId)) {
      nodeMap.set(edge.toId, { id: edge.toId, name: edge.toName, type: edge.toType })
    }
    links.push({ source: edge.fromId, target: edge.toId, type: edge.type })
  }
  return { nodes: [...nodeMap.values()], links }
}

function renderPreview() {
  const element = chartElement.value
  if (!element) {
    disposeChart()
    return
  }
  const preview = previewData.value
  if (!preview.nodes.length) {
    disposeChart()
    return
  }
  try {
    if (chart && chart.getDom() !== element) disposeChart()
    chart = chart ?? echarts.getInstanceByDom(element) ?? echarts.init(element)

    const categories: string[] = []
    for (const node of preview.nodes) {
      const type = normalizeType(node.type)
      if (!categories.includes(type)) categories.push(type)
    }
    const categoryIndex = new Map<string, number>()
    categories.forEach((name, index) => categoryIndex.set(name, index))

    const degrees = new Map<string, number>()
    for (const link of preview.links) {
      degrees.set(link.source, (degrees.get(link.source) ?? 0) + 1)
      degrees.set(link.target, (degrees.get(link.target) ?? 0) + 1)
    }
    const names = new Map<string, string>()
    for (const node of preview.nodes) names.set(node.id, node.name)
    const selectedId = selectedNode.value?.id

    chart.setOption({
      animationDuration: 400,
      tooltip: {
        trigger: 'item',
        confine: true,
        backgroundColor: '#fff',
        borderColor: '#e3ddd0',
        textStyle: { color: '#14181f', fontSize: 12, lineHeight: 18 },
        extraCssText: 'box-shadow: 0 8px 24px rgb(20 40 70 / 12%); border-radius: 4px;',
        formatter: formatTooltip
      },
      legend: {
        data: categories,
        type: 'scroll',
        bottom: 0,
        left: 'center',
        icon: 'circle',
        itemWidth: 9,
        itemHeight: 9,
        itemGap: 14,
        textStyle: { color: '#6f6a5f', fontSize: 11 }
      },
      series: [
        {
          type: 'graph',
          layout: 'force',
          roam: true,
          draggable: true,
          top: 14,
          bottom: 40,
          left: 20,
          right: 26,
          data: preview.nodes.map(node => {
            const type = normalizeType(node.type)
            const category = categoryIndex.get(type) ?? 0
            const linkCount = degrees.get(node.id) ?? 0
            const isSelected = node.id === selectedId
            return {
              id: node.id,
              name: node.name,
              category,
              symbolSize: isSelected ? 40 : Math.min(34, 20 + linkCount * 2),
              nodeType: type,
              label: {
                show: true,
                position: 'right',
                distance: 6,
                color: '#14181f',
                fontSize: 10
              },
              itemStyle: {
                color: CATEGORY_COLORS[category % CATEGORY_COLORS.length],
                borderColor: isSelected ? '#C6A96E' : '#fff',
                borderWidth: isSelected ? 3 : 1.5
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
          links: preview.links.map(link => ({
            source: link.source,
            target: link.target,
            name: relationTypeLabel(link.type),
            type: link.type,
            sourceName: names.get(link.source) ?? link.source,
            targetName: names.get(link.target) ?? link.target,
            lineStyle: { color: '#cfc7b6', width: 1.2, curveness: 0.12 }
          })),
          force: {
            repulsion: 200,
            edgeLength: [70, 120],
            gravity: 0.1,
            layoutAnimation: true
          },
          edgeSymbol: ['none', 'arrow'],
          edgeSymbolSize: 7,
          edgeLabel: {
            show: true,
            color: '#9a9487',
            fontSize: 9,
            formatter: formatEdgeLabel
          },
          lineStyle: { color: '#cfc7b6', width: 1.2, curveness: 0.12, opacity: 0.9 },
          emphasis: {
            focus: 'adjacency',
            lineStyle: { width: 2, color: '#9a9487' }
          },
          label: { show: true, position: 'right', color: '#14181f', fontSize: 10 }
        }
      ]
    }, true)
    chart.resize()
  } catch {
    disposeChart()
  }
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
    const relation = readText(data, 'type')
    if (relation) lines.push(`关系：${escapeHtml(relationTypeLabel(relation))}`)
    return lines.join('<br/>')
  }
  const name = item.name || readText(data, 'name')
  const lines = [`<b>${escapeHtml(name)}</b>`]
  const type = readText(data, 'nodeType')
  if (type) lines.push(`类型：${escapeHtml(entityTypeLabel(type))}`)
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

function entityTypeLabel(type?: string): string {
  const value = type?.trim() || ''
  return (ENTITY_TYPE_LABELS[value] ?? value) || '未分类'
}

function relationTypeLabel(type?: string): string {
  const value = type?.trim() || ''
  return (RELATION_TYPE_LABELS[value] ?? value) || '—'
}

function scopeLabel(scope?: string): string {
  if (scope === 'PUBLIC') return '公共'
  if (scope === 'PRIVATE') return '私有'
  return scope?.trim() || '—'
}

function normalizeType(type?: string): string {
  return type?.trim() || FALLBACK_TYPE
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

async function handleOpened() {
  await nextTick()
  renderPreview()
  chart?.resize()
}

function shutdown() {
  nodeRequest += 1
  edgeRequest += 1
  nodesLoading.value = false
  edgesLoading.value = false
  stopResizeListener()
  disposeChart()
}

function handleVisibleChange(value: boolean) {
  emit('update:modelValue', value)
}
</script>

<style scoped>
.gm-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  min-width: 0;
  padding-right: 26px;
}

.gm-header__title {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--lux-text-strong);
  font-family: var(--lux-font-display);
  font-size: 18px;
  font-weight: 500;
  line-height: 1.4;
  letter-spacing: 0.01em;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.gm-header__meta {
  flex: 0 0 auto;
  color: var(--lux-text-faint);
  font-family: var(--lux-font-data);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.04em;
  white-space: nowrap;
}

.gm-body {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 22px;
}

.gm-stats {
  min-width: 0;
}

.gm-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
  padding: 12px 14px;
  background: var(--lux-surface-soft);
  border: 1px solid var(--lux-line-soft);
  border-left: 2px solid var(--lux-gold-500);
  border-radius: var(--lux-radius-xs);
}

.gm-filter {
  width: 148px;
}

.gm-keyword {
  flex: 1 1 200px;
  width: auto;
  min-width: 160px;
}

.gm-filter-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-left: auto;
}

.gm-section {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 12px;
}

.gm-section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 16px;
  min-width: 0;
}

.gm-section-title {
  min-width: 0;
}

.gm-section-title h4 {
  margin: 0;
  color: var(--lux-text-strong);
  font-family: var(--lux-font-display);
  font-size: 15px;
  font-weight: 500;
  line-height: 1.4;
}

.gm-section-title p {
  margin: 4px 0 0;
  overflow: hidden;
  color: var(--lux-text-muted);
  font-size: 12px;
  line-height: 1.55;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gm-count {
  flex: 0 0 auto;
  color: var(--lux-text-faint);
  font-family: var(--lux-font-data);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.04em;
  line-height: 1.8;
}

.gm-table-wrap {
  min-width: 0;
}

.gm-table-wrap :deep(.el-table) {
  width: 100%;
  font-size: 12.5px;
}

.gm-table-wrap :deep(.el-table .cell) {
  padding: 0 8px;
}

.gm-table-wrap :deep(.el-table th.el-table__cell) {
  height: 38px;
}

.gm-table-wrap :deep(.el-table td.el-table__cell) {
  height: 42px;
}

.gm-table-wrap :deep(.el-table__empty-block) {
  min-height: 140px;
}

.gm-num {
  font-variant-numeric: tabular-nums;
}

.gm-preview {
  min-width: 0;
  padding: 12px 14px 8px;
  background: var(--lux-surface-soft);
  border: 1px solid var(--lux-line);
  border-radius: var(--lux-radius-sm);
}

.gm-canvas {
  width: 100%;
  height: 300px;
  overflow: hidden;
}

.gm-preview-hint {
  margin: 4px 0 0;
  color: var(--lux-text-faint);
  font-size: 11px;
  line-height: 1.5;
  text-align: right;
}

.gm-preview-empty {
  padding: 22px 0 14px;
  background: var(--lux-surface-soft);
  border: 1px dashed var(--lux-line-strong);
  border-radius: var(--lux-radius-xs);
}

@media (max-width: 900px) {
  .gm-filter {
    flex: 1 1 140px;
    width: auto;
  }

  .gm-filter-actions {
    margin-left: 0;
  }
}

@media (max-width: 640px) {
  .gm-header {
    flex-wrap: wrap;
    gap: 6px;
  }

  .gm-header__meta {
    white-space: normal;
  }

  .gm-filters {
    align-items: stretch;
    flex-direction: column;
  }

  .gm-filter,
  .gm-keyword {
    width: 100%;
  }

  .gm-filter-actions {
    width: 100%;
  }

  .gm-filter-actions :deep(.el-button) {
    flex: 1;
    margin-left: 0;
  }

  .gm-section-title p {
    white-space: normal;
  }

  .gm-canvas {
    height: 240px;
  }
}
</style>
