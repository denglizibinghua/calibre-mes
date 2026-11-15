<template>
  <div class="plan-panel" :aria-busy="planning || confirming">
    <p class="plan-note">
      <el-icon class="plan-note__icon" aria-hidden="true"><InfoFilled /></el-icon>
      <span>建议需要人工确认，确认后仅留存草稿，不会自动修改业务数据。</span>
    </p>

    <section class="plan-input" aria-label="排产问题">
      <el-input
        v-model="question"
        type="textarea"
        :rows="4"
        maxlength="2000"
        show-word-limit
        resize="none"
        aria-label="排产问题"
        placeholder="例如：结合设备状态和待处理工单，给出明日排产优先级。"
      />
      <div class="plan-input__foot">
        <span class="plan-scope">统计口径 · 近 <strong>{{ days }}</strong> 天</span>
        <el-button type="primary" :loading="planning" :disabled="!canSubmit" @click="submit">
          <el-icon><MagicStick /></el-icon>
          生成建议
        </el-button>
      </div>
    </section>

    <div v-if="planning" class="plan-progress" role="status" aria-live="polite">
      <el-icon class="is-loading" aria-hidden="true"><Loading /></el-icon>
      <span>{{ plan ? '正在重新生成排产建议…' : '正在生成排产建议…' }}</span>
    </div>
    <el-skeleton v-if="planning && !plan" class="plan-skeleton" :rows="4" animated />

    <section v-if="plan" class="plan-result" aria-label="排产建议">
      <div class="plan-risk" :class="`plan-risk--${riskMeta.tone}`">
        <div class="plan-risk__head">
          <el-icon class="plan-risk__icon" aria-hidden="true">
            <CircleCheckFilled v-if="riskMeta.tone === 'success'" />
            <WarningFilled v-else-if="riskMeta.tone === 'danger' || riskMeta.tone === 'warning'" />
            <InfoFilled v-else />
          </el-icon>
          <span class="plan-risk__label">风险等级</span>
          <strong class="plan-risk__value">{{ riskMeta.label }}</strong>
          <el-tag class="plan-risk__status" :type="statusMeta.type" size="small" effect="light">
            {{ statusMeta.label }}
          </el-tag>
        </div>
        <p v-if="plan.riskBasis" class="plan-risk__basis">
          <span class="plan-risk__basis-label">风险依据</span>
          <span class="plan-risk__basis-text">{{ plan.riskBasis }}</span>
        </p>
      </div>

      <p class="plan-summary">{{ plan.summary || '暂无建议摘要' }}</p>

      <section v-if="priorityOrder.length" class="plan-block">
        <h4 class="plan-block__title">建议顺序</h4>
        <ol class="plan-priority">
          <li v-for="(item, index) in priorityOrder" :key="`priority-${index}`">
            <span class="plan-priority__index" aria-hidden="true">{{ index + 1 }}</span>
            <span class="plan-priority__text">{{ item }}</span>
          </li>
        </ol>
      </section>

      <section v-for="group in tagGroups" :key="group.key" class="plan-block">
        <h4 class="plan-block__title">{{ group.label }}</h4>
        <div class="plan-tags">
          <el-tag
            v-for="(item, index) in group.items"
            :key="`${group.key}-${index}`"
            :type="group.type"
            size="small"
            effect="plain"
          >
            {{ item }}
          </el-tag>
        </div>
      </section>

      <div v-if="plan.dataTimeRange || plan.model || plan.createdAt" class="plan-meta">
        <div v-if="plan.dataTimeRange" class="plan-meta__row">
          <span class="plan-meta__label">数据时间</span>
          <span class="plan-meta__value">{{ plan.dataTimeRange }}</span>
        </div>
        <div v-if="plan.model" class="plan-meta__row">
          <span class="plan-meta__label">模型</span>
          <span class="plan-meta__value">{{ plan.model }}</span>
        </div>
        <div v-if="plan.createdAt" class="plan-meta__row">
          <span class="plan-meta__label">生成时间</span>
          <span class="plan-meta__value">{{ formatDateTime(plan.createdAt) }}</span>
        </div>
      </div>

      <div v-if="planSources.length" class="plan-sources">
        <span class="plan-sources__label">数据依据</span>
        <div class="plan-sources__list">
          <span v-for="(source, index) in planSources" :key="`source-${index}`" class="plan-source">
            {{ source }}
          </span>
        </div>
      </div>

      <el-button
        v-if="showConfirm"
        v-permission="'admin:ai:plan-confirm'"
        class="plan-confirm"
        type="warning"
        :loading="confirming"
        :disabled="confirming"
        @click="emit('confirm')"
      >
        确认建议并留存草稿
      </el-button>
      <p v-else-if="plan.status === 'PENDING_CONFIRMATION' && !canConfirm" class="plan-hint">
        当前账号无确认权限，建议仅供查看。
      </p>
    </section>

    <el-empty
      v-else-if="!planning"
      class="plan-empty"
      :image-size="76"
      description="生成排产建议后显示风险和约束"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { CircleCheckFilled, InfoFilled, Loading, MagicStick, WarningFilled } from '@element-plus/icons-vue'
import type { AiPlanSuggestion } from '@/api/ai'

const props = defineProps<{
  plan: AiPlanSuggestion | null
  planning: boolean
  confirming: boolean
  days: number
  canConfirm: boolean
}>()

const emit = defineEmits<{
  generate: [question: string]
  confirm: []
}>()

type BannerTone = 'danger' | 'warning' | 'success' | 'info'
type TagTone = 'primary' | 'success' | 'warning' | 'danger' | 'info'

interface RiskMeta {
  tone: BannerTone
  label: string
}

interface StatusMeta {
  label: string
  type: TagTone
}

interface PlanTagGroup {
  key: string
  label: string
  items: string[]
  type: TagTone
}

const RISK_META: Record<string, RiskMeta> = {
  高: { tone: 'danger', label: '高' },
  中: { tone: 'warning', label: '中' },
  低: { tone: 'success', label: '低' },
  HIGH: { tone: 'danger', label: '高' },
  MEDIUM: { tone: 'warning', label: '中' },
  LOW: { tone: 'success', label: '低' }
}

const PLAN_STATUS_META: Record<string, StatusMeta> = {
  PENDING_CONFIRMATION: { label: '待确认', type: 'warning' },
  CONFIRMED: { label: '已确认', type: 'success' },
  DRAFT: { label: '草稿', type: 'info' },
  REJECTED: { label: '已驳回', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' }
}

const question = ref('')

const canSubmit = computed(() => !props.planning && question.value.trim().length > 0)

const riskMeta = computed<RiskMeta>(() => {
  const raw = props.plan?.riskLevel?.trim() ?? ''
  if (!raw) return { tone: 'info', label: '未知' }
  return RISK_META[raw] ?? RISK_META[raw.toUpperCase()] ?? { tone: 'info', label: raw }
})

const statusMeta = computed<StatusMeta>(() => {
  const raw = props.plan?.status?.trim() ?? ''
  if (!raw) return { label: '未知', type: 'info' }
  return PLAN_STATUS_META[raw] ?? { label: raw, type: 'info' }
})

const priorityOrder = computed(() => normalizeList(props.plan?.priorityOrder))

const planSources = computed(() => normalizeList(props.plan?.sources))

const tagGroups = computed<PlanTagGroup[]>(() => {
  const current = props.plan
  if (!current) return []
  const groups: PlanTagGroup[] = [
    { key: 'orders', label: '影响工单', items: normalizeList(current.affectedOrders), type: 'primary' },
    { key: 'resources', label: '设备/工位约束', items: normalizeList(current.resourceConstraints), type: 'warning' },
    { key: 'materials', label: '物料约束', items: normalizeList(current.materialConstraints), type: 'danger' },
    { key: 'constraints', label: '约束', items: normalizeList(current.constraints), type: 'info' }
  ]
  return groups.filter(group => group.items.length > 0)
})

const showConfirm = computed(() => props.plan?.status === 'PENDING_CONFIRMATION' && props.canConfirm)

function normalizeList(items?: string[]): string[] {
  if (!items?.length) return []
  return items.map(item => item.trim()).filter(Boolean)
}

function formatDateTime(value?: string): string {
  if (!value) return ''
  const match = /^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})/.exec(value)
  return match ? `${match[1]} ${match[2]}` : value
}

function submit() {
  const text = question.value.trim()
  if (!text || props.planning) return
  emit('generate', text)
}
</script>

<style scoped>
.plan-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
  font-variant-numeric: tabular-nums;
}

.plan-note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 9px 11px;
  color: var(--lux-ember-600);
  font-size: 12px;
  line-height: 1.6;
  background: var(--lux-ember-50);
  border: 1px solid var(--lux-ember-line);
  border-radius: 10px;
}

.plan-note__icon {
  flex: 0 0 auto;
  margin-top: 2px;
  color: var(--lux-ember-500);
  font-size: 14px;
}

.plan-input {
  min-width: 0;
}

.plan-input__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
}

.plan-scope {
  color: var(--mes-text-muted, var(--lux-text-faint));
  font-size: 12px;
  white-space: nowrap;
}

.plan-scope strong {
  color: var(--mes-text-secondary, var(--lux-text-muted));
  font-weight: 650;
}

.plan-input__foot .el-button {
  min-width: 108px;
  margin-left: auto;
}

.plan-progress {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 9px 11px;
  color: var(--mes-primary, var(--lux-gold-700));
  font-size: 12px;
  line-height: 1.5;
  background: var(--mes-primary-soft, #f7f1e4);
  border-radius: 9px;
}

.plan-skeleton {
  padding: 2px 2px 0;
}

.plan-result {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
  padding: 14px;
  background: var(--mes-bg-card, #fff);
  border: 1px solid var(--mes-border, var(--lux-line));
  border-radius: 14px;
  box-shadow: var(--mes-shadow-xs, 0 2px 8px rgb(20 40 70 / 4%));
}

.plan-risk {
  padding: 11px 12px;
  border: 1px solid var(--mes-border, var(--lux-line));
  border-radius: 11px;
  background: var(--mes-bg-soft, var(--lux-surface-soft));
}

.plan-risk--danger {
  color: var(--lux-wine-600);
  background: var(--lux-wine-50);
  border-color: var(--lux-wine-line);
}

.plan-risk--warning {
  color: var(--lux-ember-600);
  background: var(--lux-ember-50);
  border-color: var(--lux-ember-line);
}

.plan-risk--success {
  color: var(--lux-jade-600);
  background: var(--lux-jade-50);
  border-color: var(--lux-jade-line);
}

.plan-risk--info {
  color: var(--mes-text-secondary, var(--lux-text-muted));
  background: var(--mes-bg-soft, var(--lux-surface-soft));
  border-color: var(--mes-border, var(--lux-line));
}

.plan-risk__head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.plan-risk__icon {
  flex: 0 0 auto;
  font-size: 14px;
}

.plan-risk--danger .plan-risk__icon {
  color: var(--lux-wine-500);
}

.plan-risk--warning .plan-risk__icon {
  color: var(--lux-ember-500);
}

.plan-risk--success .plan-risk__icon {
  color: var(--lux-jade-500);
}

.plan-risk--info .plan-risk__icon {
  color: var(--mes-text-muted, var(--lux-text-faint));
}

.plan-risk__label {
  font-size: 12px;
  font-weight: 500;
  opacity: 0.82;
}

.plan-risk__value {
  font-size: 13px;
  font-weight: 700;
}

.plan-risk__status {
  margin-left: auto;
}

.plan-risk__basis {
  display: flex;
  gap: 6px;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.65;
}

.plan-risk__basis-label {
  flex: 0 0 auto;
  font-weight: 650;
  opacity: 0.9;
}

.plan-risk__basis-text {
  min-width: 0;
  overflow-wrap: anywhere;
}

.plan-summary {
  color: var(--mes-text-primary, var(--lux-text-strong));
  font-size: 13px;
  line-height: 1.72;
  overflow-wrap: anywhere;
}

.plan-block {
  min-width: 0;
}

.plan-block__title {
  margin: 0 0 7px;
  color: var(--mes-text-secondary, var(--lux-text-muted));
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.1px;
}

.plan-priority {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.plan-priority li {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  min-width: 0;
}

.plan-priority__index {
  display: grid;
  flex: 0 0 20px;
  width: 20px;
  height: 20px;
  margin-top: 1px;
  color: var(--mes-primary, var(--lux-gold-700));
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  background: var(--mes-primary-soft, #f7f1e4);
  border-radius: 50%;
  place-items: center;
}

.plan-priority__text {
  min-width: 0;
  color: var(--mes-text-primary, var(--lux-text-strong));
  font-size: 13px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.plan-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.plan-tags :deep(.el-tag) {
  height: auto;
  min-height: 22px;
  max-width: 100%;
  padding-top: 1px;
  padding-bottom: 1px;
}

.plan-tags :deep(.el-tag__content) {
  line-height: 1.5;
  white-space: normal;
  overflow-wrap: anywhere;
}

.plan-meta {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding-top: 12px;
  border-top: 1px dashed var(--mes-border-light, var(--lux-line-soft));
}

.plan-meta__row {
  display: flex;
  gap: 8px;
  min-width: 0;
  font-size: 12px;
  line-height: 1.55;
}

.plan-meta__label {
  flex: 0 0 auto;
  color: var(--mes-text-muted, var(--lux-text-faint));
}

.plan-meta__value {
  min-width: 0;
  color: var(--mes-text-secondary, var(--lux-text-muted));
  overflow-wrap: anywhere;
}

.plan-sources {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.plan-sources__label {
  color: var(--mes-text-secondary, var(--lux-text-muted));
  font-size: 12px;
  font-weight: 650;
}

.plan-sources__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.plan-source {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  min-width: 0;
  color: var(--mes-text-muted, var(--lux-text-faint));
  font-size: 11.5px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.plan-source::before {
  content: '';
  flex: 0 0 4px;
  width: 4px;
  height: 4px;
  margin-top: 7px;
  background: var(--lux-line-strong);
  border-radius: 50%;
}

.plan-confirm {
  width: 100%;
}

.plan-hint {
  color: var(--mes-text-muted, var(--lux-text-faint));
  font-size: 12px;
  line-height: 1.6;
}

.plan-empty {
  padding: 8px 0 2px;
}

.plan-empty :deep(.el-empty__description) {
  margin-top: 4px;
}
</style>
