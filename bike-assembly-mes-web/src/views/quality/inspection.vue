<template>
  <PageContainer title="质检单总览（只读）" description="查看质检单、检验结果与缺陷记录。">
    <div class="search-bar">
      <el-input v-model="searchForm.inspectionNo" placeholder="质检单号" clearable @keyup.enter="handleSearch" />
      <el-input v-model="searchForm.itemName" placeholder="物料名称" clearable @keyup.enter="handleSearch" />
      <el-input v-model="searchForm.inspector" placeholder="检验人" clearable @keyup.enter="handleSearch" />
      <el-tag class="search-note" type="info" size="small">质检录入/确认在小程序端操作</el-tag>
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="inspectionNo" label="质检单号" min-width="180" show-overflow-tooltip />
      <el-table-column prop="workOrderId" label="工单ID" width="90" class-name="mono-num" />
      <el-table-column prop="processId" label="工序ID" width="90" class-name="mono-num" />
      <el-table-column prop="inspectionRound" label="轮次" width="70" />
      <el-table-column prop="inspector" label="检验人" width="110" show-overflow-tooltip />
      <el-table-column prop="inspectionTime" label="检验时间" min-width="160" class-name="mono-num" />
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="resultTagType(row.result)" size="small">{{ resultLabel(row.result) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button
              type="warning"
              link
              size="small"
              title="AI 质检分析"
              :disabled="row.result !== 'F' && row.result !== '2'"
              @click="handleAiAnalyze(row)"
            >
              AI 分析
            </el-button>
            <el-button type="info" link size="small" title="查看质检单详情" @click="openDetail(row)">详情</el-button>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无数据" :image-size="72" />
      </template>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next, sizes"
        :page-sizes="[10, 15, 20, 50]"
        @current-change="fetchList"
        @size-change="handleSizeChange"
      />
    </div>

    <!-- 质检单详情（只读） -->
    <el-dialog v-model="detailDialogVisible" title="质检单详情" width="760px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="质检单号">{{ currentRow?.inspectionNo }}</el-descriptions-item>
        <el-descriptions-item label="轮次">{{ currentRow?.inspectionRound ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="工单ID">{{ currentRow?.workOrderId }}</el-descriptions-item>
        <el-descriptions-item label="工序ID">{{ currentRow?.processId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="检验人">{{ currentRow?.inspector || '-' }}</el-descriptions-item>
        <el-descriptions-item label="检验时间">{{ currentRow?.inspectionTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结果">{{ resultLabel(currentRow?.result ?? '') }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(currentRow?.status ?? '') }}</el-descriptions-item>
        <el-descriptions-item label="确认人">{{ currentRow?.confirmedBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="确认时间">{{ currentRow?.confirmTime || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="detail-json">
        <pre>{{ detailJsonText }}</pre>
      </div>
    </el-dialog>

    <!-- AI 质检分析（不合格原因辅助分析） -->
    <el-dialog v-model="aiDialogVisible" title="AI 质检分析" width="640px" :close-on-click-modal="false">
      <div v-loading="aiLoading">
        <el-alert
          v-if="aiResult && !aiResult.available"
          :title="aiResult.message || 'AI 不可用'"
          type="warning"
          :closable="false"
          show-icon
        />
        <template v-if="aiResult && aiResult.available">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="模型">{{ aiResult.model || '-' }}</el-descriptions-item>
            <el-descriptions-item label="风险等级">
              <el-tag :type="riskTagType(aiResult.riskLevel)" size="small">
                {{ aiResult.riskLevel || '-' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="分析摘要">{{ aiResult.summary || '-' }}</el-descriptions-item>
          </el-descriptions>
          <h4>可能原因</h4>
          <ul v-if="aiResult.possibleCauses?.length">
            <li v-for="(c, i) in aiResult.possibleCauses" :key="i">{{ c }}</li>
          </ul>
          <el-empty v-else description="无" :image-size="40" />
          <h4>建议措施</h4>
          <ul v-if="aiResult.suggestions?.length">
            <li v-for="(s, i) in aiResult.suggestions" :key="i">{{ s }}</li>
          </ul>
          <el-empty v-else description="无" :image-size="40" />
        </template>
      </div>
      <template #footer>
        <el-button @click="aiDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { onWsConnected, useWebSocket } from '@/utils/useWebSocket'
import { useRoute } from 'vue-router'
import PageContainer from '@/components/PageContainer.vue'
import { getQmsInspectionList } from '@/api/qmsInspection'
import { analyzeDefect } from '@/api/ai'
import type { QmsInspection } from '@/types/api'

const tableData = ref<QmsInspection[]>([])
const total = ref(0)
const loading = ref(false)

let offWs: (() => void) | null = null
let offWsConnected: (() => void) | null = null
let listRequestSequence = 0
let listDisposed = false
const query = reactive({ page: 1, size: 15 })
const searchForm = reactive({ inspectionNo: '', itemName: '', inspector: '' })

const detailDialogVisible = ref(false)
const currentRow = ref<QmsInspection>()
const detailJsonText = ref('')
const aiDialogVisible = ref(false)
const aiLoading = ref(false)
const aiResult = ref<{
  available: boolean
  model?: string
  summary?: string
  possibleCauses?: string[]
  suggestions?: string[]
  riskLevel?: string
  message?: string
}>()
const route = useRoute()

onMounted(() => {
  const q = route.query.inspectionNo
  if (typeof q === 'string' && q) {
    searchForm.inspectionNo = q
  }
  fetchList()
  const ws = useWebSocket()
  ws.connect()
  offWs = ws.onWsMessage('QUALITY', () => fetchList())
  offWsConnected = onWsConnected(() => fetchList())
})

onUnmounted(() => {
  listDisposed = true
  listRequestSequence += 1
  offWs?.()
  offWsConnected?.()
})

async function fetchList() {
  if (listDisposed) return
  const requestSequence = ++listRequestSequence
  loading.value = true
  try {
    const params: Record<string, unknown> = { page: query.page, size: query.size }
    Object.keys(searchForm).forEach((k) => {
      const value = (searchForm as Record<string, string>)[k]
      if (value) params[k] = value
    })
    const res = await getQmsInspectionList(params)
    if (listDisposed || requestSequence !== listRequestSequence) return
    tableData.value = Array.isArray(res?.records) ? res.records : []
    total.value = res?.total ?? 0
  } catch {
    // 错误已由 request 拦截器统一提示
  } finally {
    if (!listDisposed && requestSequence === listRequestSequence) loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  fetchList()
}

function resetSearch() {
  searchForm.inspectionNo = ''
  searchForm.itemName = ''
  searchForm.inspector = ''
  query.page = 1
  fetchList()
}

function handleSizeChange() {
  query.page = 1
  fetchList()
}

function openDetail(row: QmsInspection) {
  currentRow.value = row
  try {
    const parsed = JSON.parse(row.detailsJson || '[]')
    detailJsonText.value = JSON.stringify(parsed, null, 2)
  } catch {
    detailJsonText.value = row.detailsJson || '[]'
  }
  detailDialogVisible.value = true
}

async function handleAiAnalyze(row: QmsInspection) {
  if (!row.inspectionId) return
  aiDialogVisible.value = true
  aiLoading.value = true
  aiResult.value = undefined
  try {
    aiResult.value = await analyzeDefect(row.inspectionId)
  } catch {
    /* 错误提示由请求拦截器统一处理 */
  } finally {
    aiLoading.value = false
  }
}

function riskTagType(level?: string): 'danger' | 'warning' | 'success' | 'info' {
  if (level === '高') return 'danger'
  if (level === '中') return 'warning'
  if (level === '低') return 'success'
  return 'info'
}

function statusLabel(status: string): string {
  const map: Record<string, string> = { '0': '待检验', '1': '已录入', '2': '已确认' }
  return map[status] || '未知'
}

function statusTagType(status: string): 'warning' | 'primary' | 'success' | 'info' {
  if (status === '0') return 'warning'
  if (status === '1') return 'primary'
  if (status === '2') return 'success'
  return 'info'
}

function resultLabel(result: string): string {
  if (result === 'P' || result === '1') return '合格'
  if (result === 'F' || result === '2') return '不合格'
  if (result === '0') return '待检'
  return '未检验'
}

function resultTagType(result: string): 'success' | 'danger' | 'info' {
  if (result === 'P' || result === '1') return 'success'
  if (result === 'F' || result === '2') return 'danger'
  return 'info'
}
</script>

<style scoped>
.search-note {
  align-self: center;
}

.detail-json {
  margin-top: 14px;
  max-height: 300px;
  overflow: auto;
  background: var(--mes-bg-soft);
  border: 1px solid var(--mes-border-light);
  border-radius: var(--mes-radius-sm);
  padding: 12px;
}

.detail-json pre {
  margin: 0;
  color: var(--mes-text-secondary);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

@media (max-width: 640px) {
  .search-note {
    align-self: flex-start;
  }
}

/* 工具条仅放主操作：右对齐 */
.table-toolbar {
  justify-content: flex-end;
}
</style>
