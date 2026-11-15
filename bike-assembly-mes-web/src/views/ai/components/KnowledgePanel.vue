<template>
  <section class="knowledge-panel" aria-label="知识库管理">
    <!-- 向量库状态 -->
    <div class="status-card">
      <div class="status-head">
        <div class="status-identity">
          <span class="status-dot" :class="`is-${vectorState.tone}`" aria-hidden="true" />
          <span class="status-caption">向量知识库</span>
          <span class="status-value">{{ vectorState.label }}</span>
        </div>
        <div class="status-actions">
          <el-button
            text
            circle
            size="small"
            :disabled="busy"
            aria-label="刷新知识库状态"
            title="刷新知识库状态"
            @click="emit('refresh-status')"
          >
            <el-icon><Refresh /></el-icon>
          </el-button>
          <el-button
            v-if="canManage"
            size="small"
            type="primary"
            plain
            :loading="indexing"
            :disabled="busy"
            @click="emit('index')"
          >
            索引知识库
          </el-button>
        </div>
      </div>

      <dl class="status-stats">
        <div class="status-stat">
          <dt>已索引文档</dt>
          <dd>{{ formatCount(indexedCount) }}</dd>
        </div>
        <div class="status-stat">
          <dt>向量块</dt>
          <dd>{{ formatCount(status?.chunksTotal) }}</dd>
        </div>
        <div class="status-stat">
          <dt>最近索引</dt>
          <dd class="status-time">{{ lastIndexedText }}</dd>
        </div>
        <div class="status-stat">
          <dt>最近任务</dt>
          <dd>
            <el-tag v-if="lastTaskMeta" size="small" :type="lastTaskMeta.type">
              {{ lastTaskMeta.label }}
            </el-tag>
            <span v-else class="status-empty">-</span>
          </dd>
        </div>
      </dl>

      <p v-if="status?.message" class="status-message">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ status?.message }}</span>
      </p>
    </div>

    <!-- 失败明细 -->
    <section v-if="failures.length" class="failure-card">
      <button
        type="button"
        class="failure-toggle"
        :aria-expanded="failuresOpen"
        aria-controls="knowledge-failure-list"
        @click="failuresOpen = !failuresOpen"
      >
        <el-icon class="failure-icon"><WarningFilled /></el-icon>
        <span class="failure-title">索引失败 {{ failures.length }} 项</span>
        <el-icon class="failure-arrow" :class="{ 'is-open': failuresOpen }"><ArrowDown /></el-icon>
      </button>
      <el-collapse-transition>
        <ul v-show="failuresOpen" id="knowledge-failure-list" class="failure-list">
          <li v-for="(failure, index) in failures" :key="`${index}-${failure}`">{{ failure }}</li>
        </ul>
      </el-collapse-transition>
    </section>

    <!-- 索引任务 -->
    <section v-if="task" class="task-card" :class="`is-${task.status.toLowerCase()}`">
      <div class="task-head">
        <span class="task-title">索引任务</span>
        <el-tag size="small" :type="taskMeta.type">{{ taskMeta.label }}</el-tag>
      </div>

      <template v-if="isTaskActive">
        <el-progress
          v-if="progressPercent !== null"
          :percentage="progressPercent ?? 0"
          :stroke-width="8"
          :show-text="false"
        />
        <el-progress
          v-else
          :percentage="0"
          :indeterminate="true"
          :duration="2"
          :stroke-width="8"
          :show-text="false"
        />
        <div class="task-progress-text">
          <span>已处理 {{ task.chunksIndexed }} / {{ task.chunksTotal }} 个分块</span>
          <span v-if="progressPercent !== null" class="task-percent">{{ progressPercent }}%</span>
        </div>
        <div class="task-stats">
          <span class="task-stat"><em>扫描</em>{{ task.scannedFiles }}</span>
          <span class="task-stat"><em>变更</em>{{ task.filesChanged }}</span>
          <span class="task-stat"><em>跳过</em>{{ task.filesSkipped }}</span>
          <span class="task-stat"><em>删除</em>{{ task.filesDeleted }}</span>
        </div>
      </template>

      <el-alert
        v-else-if="task.status === 'COMPLETED'"
        type="success"
        :closable="false"
        show-icon
        title="索引已完成"
        :description="completedDescription"
      />
      <el-alert
        v-else
        type="error"
        :closable="false"
        show-icon
        title="索引失败"
        :description="task.message || '请查看后端日志排查失败原因。'"
      />
    </section>

    <p class="index-hint">
      <el-icon><InfoFilled /></el-icon>
      <span>索引长时间停留在执行中？Office 文档解析异常可能导致任务不结束，请查看后端日志。</span>
    </p>

    <!-- 文件管理 -->
    <section class="files-section" aria-label="知识文件">
      <div class="file-toolbar">
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索文件名"
          class="search-input"
          aria-label="搜索文件名"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>

        <div class="toolbar-row">
          <el-select v-model="scopeFilter" size="small" class="toolbar-select" aria-label="按范围筛选">
            <el-option label="全部范围" value="ALL" />
            <el-option label="公共" value="PUBLIC" />
            <el-option label="个人" value="PRIVATE" />
          </el-select>
          <el-select v-model="statusFilter" size="small" class="toolbar-select" aria-label="按状态筛选">
            <el-option label="全部状态" value="ALL" />
            <el-option label="已索引" value="INDEXED" />
            <el-option label="待索引" value="PENDING" />
            <el-option label="失败" value="FAILED" />
          </el-select>
          <el-select
            v-if="canManage"
            v-model="uploadScope"
            size="small"
            class="toolbar-select"
            aria-label="上传范围"
          >
            <el-option label="个人私有" value="PRIVATE" />
            <el-option label="系统公共" value="PUBLIC" />
          </el-select>
        </div>

        <div class="toolbar-actions">
          <input
            ref="fileInput"
            class="file-input"
            type="file"
            accept=".md,.txt,.pdf,.docx,.xlsx,.pptx"
            @change="handleFileSelected"
          />
          <el-button type="primary" :loading="busy" @click="chooseFile">
            <el-icon><Upload /></el-icon>
            {{ replaceTarget ? '选择替换文件' : '上传文件' }}
          </el-button>
          <el-button
            circle
            :loading="filesLoading"
            :disabled="busy"
            aria-label="刷新文件列表"
            title="刷新文件列表"
            @click="emit('refresh-files')"
          >
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>

        <div v-if="replaceTarget" class="replace-banner">
          <span class="replace-text">正在替换：{{ replaceTarget.name }}</span>
          <el-button link type="warning" @click="cancelReplace">取消替换</el-button>
        </div>

        <div v-if="files.length" class="file-summary">{{ fileSummary }}</div>
      </div>

      <div class="table-wrap">
        <el-table
          v-loading="filesLoading"
          element-loading-text="加载知识文件…"
          :data="filteredFiles"
          size="small"
          row-key="documentId"
          :max-height="380"
        >
          <el-table-column prop="name" label="文件名" min-width="180" show-overflow-tooltip />
          <el-table-column label="范围" width="72">
            <template #default="{ row }">
              <el-tag size="small" :type="row.scope === 'PUBLIC' ? 'success' : 'info'">
                {{ row.scope === 'PUBLIC' ? '公共' : '个人' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="分块" width="64" align="right">
            <template #default="{ row }">
              <span class="num">{{ row.chunkCount ?? '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="索引状态" width="88">
            <template #default="{ row }">
              <el-tag size="small" :type="indexStatusMeta(row.indexStatus).type">
                {{ indexStatusMeta(row.indexStatus).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="大小" width="76" align="right">
            <template #default="{ row }">
              <span class="num">{{ formatFileSize(row.size) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="126">
            <template #default="{ row }">
              <span class="num">{{ row.indexedAt ? formatDateTime(row.indexedAt) : '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right" align="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button
                  link
                  :disabled="busy"
                  aria-label="下载文件"
                  title="下载文件"
                  @click="emit('download', row)"
                >
                  <el-icon><Download /></el-icon>
                </el-button>
                <template v-if="row.scope === 'PRIVATE' || canManage">
                  <el-button
                    link
                    :disabled="busy"
                    aria-label="替换文件"
                    title="替换文件"
                    @click="beginReplace(row)"
                  >
                    <el-icon><Edit /></el-icon>
                  </el-button>
                  <el-button
                    link
                    type="danger"
                    :disabled="busy"
                    aria-label="删除文件"
                    title="删除文件"
                    @click="emit('delete', row.documentId)"
                  >
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </template>
              </div>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty :description="emptyDescription" :image-size="72" />
          </template>
        </el-table>
      </div>

      <p class="scope-note">公共资料对所有用户开放；私有资料仅当前账号可访问。</p>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  ArrowDown,
  Delete,
  Download,
  Edit,
  InfoFilled,
  Refresh,
  Search,
  Upload,
  WarningFilled
} from '@element-plus/icons-vue'
import type { AiKnowledgeFile, AiKnowledgeIndexResult, AiKnowledgeTask } from '@/api/ai'
import {
  formatDateTime,
  formatFileSize,
  indexStatusMeta,
  taskStatusMeta
} from '@/views/ai/citation'

const props = defineProps<{
  status: AiKnowledgeIndexResult | null
  task: AiKnowledgeTask | null
  files: AiKnowledgeFile[]
  filesLoading: boolean
  busy: boolean
  indexing: boolean
  canManage: boolean
}>()

const emit = defineEmits<{
  'refresh-status': []
  'refresh-files': []
  index: []
  upload: [file: File, scope: 'PUBLIC' | 'PRIVATE']
  replace: [documentId: number, file: File]
  delete: [documentId: number]
  download: [file: AiKnowledgeFile]
}>()

type ScopeFilter = 'ALL' | 'PUBLIC' | 'PRIVATE'
type StatusFilter = 'ALL' | 'INDEXED' | 'PENDING' | 'FAILED'
type Tone = 'success' | 'warning' | 'danger' | 'muted'

interface VectorState {
  label: string
  tone: Tone
}

const keyword = ref('')
const scopeFilter = ref<ScopeFilter>('ALL')
const statusFilter = ref<StatusFilter>('ALL')
const uploadScope = ref<'PUBLIC' | 'PRIVATE'>('PRIVATE')
const replaceTarget = ref<AiKnowledgeFile | null>(null)
const failuresOpen = ref(true)
const fileInput = ref<HTMLInputElement>()

const pendingStatuses = new Set(['PENDING', 'QUEUED', 'RUNNING', 'INDEXING'])

const vectorState = computed<VectorState>(() => {
  if (!props.status) return { label: '未知', tone: 'muted' }
  switch (props.status.vectorStoreStatus) {
    case 'ENABLED':
      return { label: '已启用', tone: 'success' }
    case 'NOT_CONFIGURED':
      return { label: '未配置', tone: 'warning' }
    case 'UNAVAILABLE':
      return { label: '不可用', tone: 'danger' }
    default:
      return props.status.vectorStoreEnabled
        ? { label: '已启用', tone: 'success' }
        : { label: '未知', tone: 'muted' }
  }
})

const indexedCount = computed(() => (
  props.status ? (props.status.indexedFiles ?? props.status.scannedFiles) : null
))

const lastIndexedText = computed(() => {
  const indexedAt = props.status?.indexedAt
  return indexedAt ? formatDateTime(indexedAt) : '-'
})

const taskMeta = computed(() => taskStatusMeta(props.task?.status))

const lastTaskMeta = computed(() => (
  props.status?.lastTaskStatus ? taskStatusMeta(props.status.lastTaskStatus) : null
))

const isTaskActive = computed(() => (
  props.task?.status === 'QUEUED' || props.task?.status === 'RUNNING'
))

const progressPercent = computed<number | null>(() => {
  if (!props.task || props.task.chunksTotal <= 0) return null
  const ratio = props.task.chunksIndexed / props.task.chunksTotal
  return Math.min(100, Math.max(0, Math.round(ratio * 100)))
})

const completedDescription = computed(() => {
  const current = props.task
  if (!current) return ''
  return `扫描 ${current.scannedFiles} 个文件，变更 ${current.filesChanged} 个，跳过 ${current.filesSkipped} 个，删除 ${current.filesDeleted} 个，处理 ${current.chunksIndexed} 个分块。`
})

const failures = computed(() => {
  const items: string[] = []
  const seen = new Set<string>()
  const add = (value: string) => {
    const text = value.trim()
    if (!text || seen.has(text)) return
    seen.add(text)
    items.push(text)
  }
  props.status?.failures?.forEach(add)
  props.task?.failures?.forEach(add)
  return items
})

const filteredFiles = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return props.files.filter(file => {
    if (search && !file.name.toLowerCase().includes(search)) return false
    if (scopeFilter.value !== 'ALL' && file.scope !== scopeFilter.value) return false
    if (statusFilter.value === 'INDEXED' && file.indexStatus !== 'INDEXED') return false
    if (statusFilter.value === 'FAILED' && file.indexStatus !== 'FAILED') return false
    if (statusFilter.value === 'PENDING' && !pendingStatuses.has(file.indexStatus)) return false
    return true
  })
})

const filtersActive = computed(() => (
  keyword.value.trim() !== '' || scopeFilter.value !== 'ALL' || statusFilter.value !== 'ALL'
))

const fileSummary = computed(() => (
  filtersActive.value
    ? `筛选结果 ${filteredFiles.value.length} / ${props.files.length} 个文件`
    : `共 ${props.files.length} 个文件`
))

const emptyDescription = computed(() => (
  props.files.length > 0 ? '没有匹配的知识文件' : '暂无知识文件'
))

function formatCount(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return '-'
  return value.toLocaleString('zh-CN')
}

function chooseFile() {
  if (props.busy) return
  fileInput.value?.click()
}

function beginReplace(file: AiKnowledgeFile) {
  if (props.busy) return
  replaceTarget.value = file
  fileInput.value?.click()
}

function cancelReplace() {
  replaceTarget.value = null
}

function handleFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  const target = replaceTarget.value
  replaceTarget.value = null
  if (target) {
    emit('replace', target.documentId, file)
  } else {
    emit('upload', file, uploadScope.value)
  }
}
</script>

<style scoped>
.knowledge-panel {
  --kp-success: var(--lux-jade-500);
  --kp-warning: var(--lux-ember-500);
  --kp-danger: var(--lux-wine-500);
  --kp-muted: var(--lux-text-faint);

  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  color: var(--mes-text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.status-card {
  min-width: 0;
  padding: 14px;
  background: linear-gradient(180deg, var(--lux-surface-soft) 0%, var(--mes-bg-card) 72%);
  border: 1px solid var(--mes-border-light);
  border-radius: var(--mes-radius-lg);
  box-shadow: var(--mes-shadow-xs);
}

.status-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.status-identity {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.status-dot {
  flex: none;
  width: 9px;
  height: 9px;
  background: var(--kp-muted);
  border-radius: 50%;
}

.status-dot.is-success {
  background: var(--kp-success);
  box-shadow: 0 0 0 3px rgb(63 143 121 / 12%);
}

.status-dot.is-warning {
  background: var(--kp-warning);
  box-shadow: 0 0 0 3px rgb(184 117 46 / 12%);
}

.status-dot.is-danger {
  background: var(--kp-danger);
  box-shadow: 0 0 0 3px rgb(168 69 79 / 12%);
}

.status-dot.is-muted {
  background: var(--kp-muted);
  box-shadow: 0 0 0 3px rgb(155 168 184 / 14%);
}

.status-caption {
  color: var(--mes-text-muted);
  font-size: 12px;
}

.status-value {
  color: var(--mes-text-primary);
  font-size: 14px;
  font-weight: 650;
  letter-spacing: 0.1px;
}

.status-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}

.status-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.status-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 12px;
}

.status-stat {
  min-width: 0;
  padding: 7px 9px;
  background: rgb(255 255 255 / 82%);
  border: 1px solid var(--mes-border-light);
  border-radius: var(--mes-radius-sm);
}

.status-stat dt {
  color: var(--mes-text-muted);
  font-size: 11px;
  line-height: 1.4;
}

.status-stat dd {
  margin-top: 2px;
  overflow: hidden;
  color: var(--mes-text-primary);
  font-size: 14px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-stat dd.status-time {
  font-size: 12.5px;
  font-weight: 600;
}

.status-empty {
  color: var(--mes-text-placeholder);
  font-weight: 500;
}

.status-message {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 10px;
  color: var(--mes-text-muted);
  font-size: 12px;
  line-height: 1.5;
  word-break: break-word;
}

.status-message .el-icon {
  flex: none;
  margin-top: 2px;
}

.failure-card {
  overflow: hidden;
  background: var(--el-color-danger-light-9, var(--lux-wine-50));
  border: 1px solid var(--el-color-danger-light-7, var(--lux-wine-line));
  border-left: 3px solid var(--kp-danger);
  border-radius: var(--mes-radius-sm);
}

.failure-toggle {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  color: var(--el-color-danger-dark-2, var(--lux-wine-600));
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  text-align: left;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.failure-icon {
  flex: none;
  color: var(--el-color-danger, var(--kp-danger));
}

.failure-title {
  flex: 1;
  min-width: 0;
}

.failure-arrow {
  flex: none;
  color: var(--el-color-danger, var(--kp-danger));
  opacity: 0.72;
  transition: transform var(--mes-transition-fast);
}

.failure-arrow.is-open {
  transform: rotate(180deg);
}

.failure-list {
  max-height: 132px;
  margin: 0;
  padding: 2px 10px 10px 28px;
  overflow-y: auto;
  color: var(--el-color-danger-dark-2, var(--lux-wine-600));
  font-size: 12px;
  line-height: 1.6;
  list-style: disc;
}

.failure-list li {
  overflow-wrap: anywhere;
}

.failure-list li + li {
  margin-top: 4px;
}

.task-card {
  min-width: 0;
  padding: 12px;
  background: var(--mes-bg-soft);
  border: 1px solid var(--mes-border-light);
  border-radius: var(--mes-radius-md);
}

.task-card.is-queued,
.task-card.is-running {
  background: linear-gradient(180deg, var(--lux-surface-soft) 0%, #fff 100%);
  border-color: var(--lux-line);
}

.task-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.task-title {
  color: var(--mes-text-primary);
  font-size: 13px;
  font-weight: 650;
}

.task-card :deep(.el-progress) {
  margin-top: 10px;
}

.task-card :deep(.el-progress-bar__outer) {
  background: var(--lux-line-soft);
  border-radius: 99px;
}

.task-card :deep(.el-progress-bar__inner) {
  border-radius: 99px;
}

.task-progress-text {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 6px;
  color: var(--mes-text-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.task-percent {
  color: var(--mes-primary);
  font-weight: 600;
}

.task-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.task-stat {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  padding: 2px 7px;
  color: var(--mes-text-primary);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  background: #fff;
  border: 1px solid var(--mes-border-light);
  border-radius: 6px;
}

.task-stat em {
  color: var(--mes-text-muted);
  font-size: 11px;
  font-style: normal;
  font-weight: 500;
}

.task-card :deep(.el-alert) {
  margin-top: 10px;
  padding: 8px 10px;
}

.task-card :deep(.el-alert__title) {
  font-size: 12.5px;
}

.task-card :deep(.el-alert__description) {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.55;
}

.index-hint {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 0;
  padding: 0 2px;
  color: var(--mes-text-muted);
  font-size: 11.5px;
  line-height: 1.55;
}

.index-hint .el-icon {
  flex: none;
  margin-top: 2px;
  color: var(--mes-text-placeholder);
}

.files-section {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 10px;
}

.file-toolbar {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 8px;
}

.search-input {
  width: 100%;
}

.toolbar-row {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 8px;
}

.toolbar-select {
  flex: 1 1 96px;
  min-width: 0;
}

.toolbar-select :deep(.el-select__wrapper) {
  min-height: 32px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.toolbar-actions .el-button:first-of-type {
  flex: 1;
}

.file-input {
  display: none;
}

.replace-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 9px;
  color: var(--lux-ember-600);
  font-size: 12px;
  background: var(--el-color-warning-light-9, var(--lux-ember-50));
  border: 1px solid var(--el-color-warning-light-7, var(--lux-ember-line));
  border-radius: 6px;
}

.replace-banner :deep(.el-button + .el-button) {
  margin-left: 0;
}

.replace-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-summary {
  color: var(--mes-text-muted);
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
}

.table-wrap {
  min-width: 0;
}

.table-wrap :deep(.el-table) {
  width: 100%;
  font-size: 12.5px;
}

.table-wrap :deep(.el-table .cell) {
  padding: 0 8px;
}

.table-wrap :deep(.el-table th.el-table__cell) {
  height: 38px;
}

.table-wrap :deep(.el-table td.el-table__cell) {
  height: 42px;
}

.table-wrap :deep(.el-table__empty-block) {
  min-height: 150px;
}

.num {
  font-variant-numeric: tabular-nums;
}

.row-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
}

.row-actions :deep(.el-button) {
  min-height: 24px;
  margin-left: 0;
  padding: 2px 3px;
}

.scope-note {
  margin: 0;
  color: var(--mes-text-placeholder);
  font-size: 11.5px;
  line-height: 1.55;
}

@media (max-width: 560px) {
  .knowledge-panel {
    gap: 10px;
  }

  .status-card {
    padding: 12px;
  }

  .status-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .toolbar-row {
    flex-direction: column;
  }

  .toolbar-select {
    flex: 1 1 auto;
    width: 100%;
  }

  .toolbar-actions {
    width: 100%;
  }
}
</style>
