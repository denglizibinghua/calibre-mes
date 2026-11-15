<template>
  <div ref="screenElement" class="production-screen" :aria-busy="loading">
    <header class="screen-heading">
      <div class="screen-heading-copy"><span class="screen-context">自行车装配 MES / 生产看板</span><h1>生产运营指挥中心<span class="heading-dot" aria-hidden="true" /></h1></div>
      <div class="screen-clock"><strong>{{ timeText }}</strong><span>{{ dateText }}</span></div>
    </header>

    <div class="screen-toolbar">
      <div class="screen-filters">
        <label class="unit-filter"><span>生产单元</span><select v-model="workUnitId" aria-label="选择车间或产线"><option value="">{{ data?.dataScope === 'SELF' ? '本人生产单元' : '全部车间 / 产线' }}</option><option v-for="unit in data?.workUnits || []" :key="unit.unitId" :value="String(unit.unitId)">{{ unit.unitName }}</option></select></label>
        <div class="period-switch" role="group" aria-label="统计周期"><button v-for="period in [7, 14, 30]" :key="period" type="button" :aria-pressed="days === period" :class="{ active: days === period }" @click="days = period">近 {{ period }} 天</button></div>
      </div>
      <div class="screen-actions">
        <span class="update-state" :class="{ stale: error, paused: !autoRefresh }"><i />{{ loading ? '更新中' : error ? '数据待更新' : autoRefresh ? `${countdown}s 后刷新` : '自动刷新已暂停' }}</span>
        <button class="screen-icon-button" type="button" :aria-label="autoRefresh ? '暂停自动刷新' : '开启自动刷新'" :title="autoRefresh ? '暂停自动刷新' : '开启自动刷新'" :aria-pressed="autoRefresh" @click="autoRefresh = !autoRefresh"><VideoPause v-if="autoRefresh" /><VideoPlay v-else /></button>
        <button class="screen-icon-button" type="button" aria-label="刷新数据" title="刷新数据" :disabled="loading" @click="refresh"><Refresh :class="{ spinning: loading }" /></button>
        <button class="screen-icon-button" type="button" :aria-label="isFullscreen ? '退出全屏' : '全屏展示'" :title="isFullscreen ? '退出全屏' : '全屏展示'" @click="toggleFullscreen"><FullScreen /></button>
      </div>
    </div>

    <div v-if="error" class="screen-error" role="alert"><Warning /><span>{{ data ? `更新失败，当前显示 ${formatTime(data.generatedAt)} 的数据。` : '暂时无法加载看板数据。' }}{{ error }}</span><button type="button" :disabled="loading" @click="refresh">重试</button></div>
    <div v-if="!data" class="screen-initial" role="status"><Refresh v-if="loading" class="spinning" /><strong>{{ loading ? '正在汇总生产数据' : '暂无可显示的数据' }}</strong><span>生产、质检、设备与安灯数据将在这里集中展示</span></div>

    <template v-else>
      <div class="snapshot-scope" role="status"><span>当前显示：<strong>{{ appliedUnitName }}</strong> · {{ data.startDate }} — {{ data.endDate }}</span><span v-if="filtersPending" class="signal-warning">新筛选尚未应用，{{ loading ? '正在加载' : '请重试' }}；以下仍为已加载范围的数据</span></div>
      <div v-if="data.dataScope === 'SELF'" class="scope-note">当前仅展示本人指派工单及关联记录，安灯包含本人上报或处理的告警。</div>
      <section class="screen-metrics" aria-label="核心生产指标">
        <button class="screen-metric" type="button" @click="jumpTo('screen-orders')"><span class="metric-label">在制工单<ArrowRight /></span><strong>{{ number(data.summary.activeWorkOrders) }}<small>单</small></strong><span class="metric-foot"><span :class="{ 'signal-warning': data.summary.overdueWorkOrders > 0 }">{{ data.summary.overdueWorkOrders }} 单逾期</span><span>生产中 / 已下达 / 暂停</span></span></button>
        <button class="screen-metric" type="button" @click="jumpTo('screen-production')"><span class="metric-label">今日完工<ArrowRight /></span><strong class="signal-green">{{ number(data.summary.todayCompletedWorkOrders) }}<small>单</small></strong><span class="metric-foot"><span>按实际完工时间统计</span><span>{{ data.endDate }}</span></span></button>
        <button class="screen-metric" type="button" @click="jumpTo('screen-quality')"><span class="metric-label">初检合格率<ArrowRight /></span><strong>{{ rate(data.quality.firstPassRate) }}<small v-if="data.quality.firstPassRate !== null">%</small></strong><span class="metric-foot"><span>合格 {{ data.quality.passed }} / 初检 {{ data.quality.inspected }} 单</span><span>近 {{ displayDays }} 天</span></span></button>
        <button class="screen-metric" type="button" @click="jumpTo('screen-equipment')"><span class="metric-label">设备可用率<ArrowRight /></span><strong>{{ rate(data.equipment.availabilityRate) }}<small v-if="data.equipment.availabilityRate !== null">%</small></strong><span class="metric-foot"><span>{{ data.equipment.available }} 台可用</span><span :class="{ 'signal-warning': data.equipment.fault > 0 }">{{ data.equipment.fault }} 台故障 / 维修</span></span></button>
      </section>

      <div class="operations-grid">
        <section id="screen-processes" class="screen-panel process-panel">
          <header class="panel-heading"><div><h2><SetUp />装配工序监控</h2><p>当前在制工单的工序负荷 · 点击工序查看明细</p></div><span class="count-tag">{{ data.processes.length }} 道工序</span></header>
          <AssemblyFlow :processes="data.processes" :selected-id="selectedProcess" @select="selectedProcess = $event" />
          <footer class="panel-footnote"><span><i class="legend-dot green" />作业中</span><span><i class="legend-dot amber" />不合格 / 返工</span><span><i class="legend-dot gray" />待流转</span><button v-if="modulePath('工序')" type="button" @click="openModule('工序')">工序管理<ArrowRight /></button></footer>
        </section>

        <section class="screen-panel alerts-panel">
          <header class="panel-heading"><div><h2><Warning />安灯告警</h2><p>未关闭告警 · 按等级、等待时间排序</p></div><span class="alert-count" :class="{ 'signal-warning': data.summary.openAlerts > 0 }">{{ data.summary.openAlerts }}</span></header>
          <div v-if="!data.alerts.length" class="screen-empty"><CircleCheck /><strong>当前无待处理告警</strong><span>所选生产单元暂无未关闭的安灯记录</span></div>
          <ul v-else class="alert-stream"><li v-for="alert in data.alerts" :key="alert.alertId" :class="{ severe: alert.alertLevel === '3' }"><div class="alert-line"><span class="severity-label">{{ severity[alert.alertLevel] || '未分级' }}</span><time :datetime="alert.createTime">{{ elapsed(alert.createTime) }}</time></div><strong>{{ alert.alertType }}</strong><p>{{ alert.unitName || '未关联生产单元' }}<span v-if="alert.equipmentName"> / {{ alert.equipmentName }}</span></p><div class="alert-bottom"><span>{{ alert.alertNo }}</span><span>{{ alert.status === '0' ? '待处理' : '处理中' }}</span></div></li></ul>
          <footer class="panel-footnote"><span>显示优先级最高的 {{ data.alerts.length }} 条</span><button v-if="modulePath('安灯')" type="button" @click="openModule('安灯')">处理告警<ArrowRight /></button></footer>
        </section>
      </div>

      <div class="analytics-grid">
        <section id="screen-production" class="screen-panel production-panel">
          <header class="panel-heading"><div><h2><TrendCharts />生产达成趋势</h2><p>{{ data.startDate }} — {{ data.endDate }} · 单位：工单</p></div><span class="count-tag">{{ completedInPeriod }} 单完工</span></header>
          <div class="chart-legend"><span><i class="legend-dot green" />实际完工</span><span><i class="legend-line" />计划完工</span></div>
          <div class="production-chart"><ScreenChart :option="productionChart" :label="`近${displayDays}天计划完工与实际完工工单趋势`" /></div>
          <footer class="panel-footnote">计划按计划结束日统计，实际按完工日统计；不累加工序报工数量。</footer>
        </section>

        <section id="screen-quality" class="screen-panel quality-panel">
          <header class="panel-heading"><div><h2><CircleCheck />质量追踪</h2><p>已确认初检单 · 不包含复检</p></div><span class="count-tag">近 {{ displayDays }} 天</span></header>
          <div class="quality-summary"><div><strong>{{ rate(data.quality.firstPassRate) }}<small v-if="data.quality.firstPassRate !== null">%</small></strong><span>初检合格率</span></div><div class="quality-pending"><b>{{ data.summary.pendingInspections }}</b><span>待检 / 待确认</span></div></div>
          <div class="quality-chart"><ScreenChart :option="qualityChart" label="每日初检合格率，无检验记录的日期留空" /></div>
          <footer class="panel-footnote">{{ data.quality.inspected ? `初检 ${data.quality.inspected} 单，合格 ${data.quality.passed} 单` : '统计期内暂无已确认的初检记录' }}</footer>
        </section>

        <section id="screen-equipment" class="screen-panel equipment-panel">
          <header class="panel-heading"><div><h2><Monitor />设备状态</h2><p>设备台账当前状态</p></div></header>
          <div class="equipment-visual"><ScreenChart :option="equipmentChart" label="可用、故障维修和停用设备数量分布" /><div class="equipment-center"><strong>{{ equipmentTotal }}</strong><span>设备总数 / 台</span></div></div>
          <div class="equipment-legend"><span><i class="legend-dot green" />可用<b>{{ data.equipment.available }}</b></span><span><i class="legend-dot amber" />故障 / 维修<b>{{ data.equipment.fault }}</b></span><span><i class="legend-dot gray" />停用<b>{{ data.equipment.disabled }}</b></span></div>
          <footer class="panel-footnote">可用率 = 正常设备 / 启用设备，不含停用设备。</footer>
        </section>
      </div>

      <div class="tracking-grid">
        <section id="screen-orders" class="screen-panel orders-panel">
          <header class="panel-heading"><div><h2><List />重点工单追踪</h2><p>逾期、暂停工单优先 · 最多展示 12 条</p></div><button v-if="modulePath('工单')" class="panel-link" type="button" @click="openModule('工单')">全部工单<ArrowRight /></button></header>
          <div class="orders-toolbar"><div class="order-tabs" role="group" aria-label="筛选当前工单列表"><button v-for="tab in orderTabs" :key="tab.value" type="button" :class="{ active: orderFilter === tab.value }" :aria-pressed="orderFilter === tab.value" @click="orderFilter = tab.value">{{ tab.label }}</button></div><label class="order-search"><Search /><input v-model="search" type="search" aria-label="搜索当前工单" placeholder="搜索当前工单" /></label></div>
          <div class="orders-table-wrap"><table class="orders-table"><thead><tr><th>工单 / 产品</th><th>生产单元</th><th>计划数量</th><th>工序进度</th><th>计划完成</th><th>状态</th><th><span class="sr-only">操作</span></th></tr></thead><tbody><tr v-for="order in filteredOrders" :key="order.workOrderId"><td><strong class="order-code">{{ order.workOrderNo }}</strong><span class="order-product">{{ order.itemName || '未关联产品' }}</span></td><td>{{ order.unitName || '未分配' }}</td><td class="nowrap">{{ number(order.planQty) }} {{ order.unit || '' }}</td><td><div class="order-progress"><span><i :style="{ width: `${progress(order)}%` }" /></span><b>{{ order.totalSteps ? `${progress(order)}%` : '—' }}</b></div><small>{{ order.completedSteps }} / {{ order.totalSteps }} 道工序</small></td><td class="nowrap" :class="{ 'signal-warning': order.overdue }">{{ shortDate(order.planEndTime) }}<small v-if="order.overdue">已逾期</small></td><td><span class="order-status" :class="`status-${order.status}`">{{ orderStatuses[order.status] || '未知状态' }}</span></td><td><button class="table-detail" type="button" :aria-label="`查看${order.workOrderNo}详情`" @click="showOrder(order)"><ArrowRight /></button></td></tr><tr v-if="!filteredOrders.length"><td colspan="7" class="table-empty">{{ search || orderFilter !== 'all' ? '当前列表中没有匹配的工单' : '所选生产单元暂无在制或统计期内完工的工单' }}</td></tr></tbody></table></div>
          <footer class="panel-footnote"><span>当前显示 {{ filteredOrders.length }} 条 / 已加载 {{ data.workOrders.length }} 条</span><span>进度按已完成及跳过工序计算</span></footer>
        </section>

        <section class="screen-panel defects-panel"><header class="panel-heading"><div><h2><DataAnalysis />不良原因排行</h2><p>近 {{ displayDays }} 天 · 按缺陷记录数统计</p></div><span class="count-tag">前 5 项</span></header><div v-if="!data.defects.length" class="screen-empty"><CircleCheck /><strong>统计期内暂无不良记录</strong><span>登记缺陷后将自动汇总分类排行</span></div><ol v-else class="defect-ranking"><li v-for="(defect, index) in data.defects" :key="defect.name"><span class="rank-number">{{ String(index + 1).padStart(2, '0') }}</span><div><span>{{ defect.name }}<strong>{{ defect.value }}<small>条</small></strong></span><span class="defect-bar"><i :style="{ width: `${defect.value / Math.max(1, ...data.defects.map(d => d.value)) * 100}%` }" /></span></div></li></ol><footer class="panel-footnote">同一产品可能有多条缺陷记录，不等同于不良品数量。</footer></section>
      </div>

      <footer class="screen-footer"><span><i class="legend-dot" :class="error ? 'amber' : 'green'" />{{ error ? '数据待更新' : '数据已同步' }} · {{ formatTime(data.generatedAt) }}</span><span>统计时区 Asia/Shanghai · {{ autoRefresh ? '每 30 秒更新' : '自动更新已暂停' }}</span></footer>
    </template>

    <dialog ref="orderDialog" class="screen-order-dialog" aria-labelledby="order-dialog-title" @click="onDialogClick" @close="selectedOrder = null">
      <template v-if="selectedOrder"><header><div><span class="muted">工单详情</span><h2 id="order-dialog-title">{{ selectedOrder.workOrderNo }}</h2></div><button class="screen-icon-button" type="button" aria-label="关闭详情" @click="orderDialog?.close()"><Close /></button></header><dl class="order-detail-grid"><div><dt>产品</dt><dd>{{ selectedOrder.itemName || '未关联产品' }}</dd></div><div><dt>生产单元</dt><dd>{{ selectedOrder.unitName || '未分配' }}</dd></div><div><dt>计划数量</dt><dd>{{ number(selectedOrder.planQty) }} {{ selectedOrder.unit }}</dd></div><div><dt>工单状态</dt><dd>{{ orderStatuses[selectedOrder.status] || '未知状态' }}</dd></div><div><dt>计划完成</dt><dd>{{ selectedOrder.planEndTime || '未设置' }}</dd></div><div><dt>工序进度</dt><dd>{{ selectedOrder.completedSteps }} / {{ selectedOrder.totalSteps }} 道</dd></div></dl><p class="dialog-note">进度依据实际工序记录计算，已跳过工序计入完成，已取消工序不计入总数。</p><button v-if="modulePath('工单')" class="screen-primary" type="button" @click="openModule('工单')">前往工单管理<ArrowRight /></button></template>
    </dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, CircleCheck, Close, DataAnalysis, FullScreen, List, Monitor, Refresh, Search, SetUp, TrendCharts, VideoPause, VideoPlay, Warning } from '@element-plus/icons-vue'
import type { ScreenWorkOrder } from '@/api/dashboard'
import AssemblyFlow from './components/AssemblyFlow.vue'
import ScreenChart from './components/ScreenChart.vue'
import { productionOption, qualityOption, equipmentOption } from './chartOptions'
import { useProductionScreen } from './useProductionScreen'
import { findModulePath, type DashboardModule } from './moduleNavigation'

const router = useRouter()
const { days, workUnitId, data, appliedQuery, filtersPending, loading, error, autoRefresh, countdown, clock, refresh } = useProductionScreen()
const displayDays = computed(() => appliedQuery.value?.days ?? days.value)
const appliedUnitName = computed(() => {
  const id = appliedQuery.value?.workUnitId
  return id ? data.value?.workUnits.find(unit => String(unit.unitId) === id)?.unitName || `生产单元 ${id}`
    : data.value?.dataScope === 'SELF' ? '本人生产单元' : '全部车间 / 产线'
})
const screenElement = ref<HTMLDivElement>()
const orderDialog = ref<HTMLDialogElement>()
const selectedOrder = ref<ScreenWorkOrder | null>(null)
const selectedProcess = ref<number | null>(null)
const isFullscreen = ref(false)
const search = ref('')
const orderFilter = ref('all')
const orderTabs = [{ value: 'all', label: '全部' }, { value: 'overdue', label: '已逾期' }, { value: 'paused', label: '已暂停' }]
const orderStatuses: Record<string, string> = { '0': '已创建', '1': '生产中', '2': '已完工', '3': '已关闭', '4': '已下达', '5': '已暂停', '6': '已取消' }
const severity: Record<string, string> = { '1': '一般', '2': '紧急', '3': '严重' }
const timeText = computed(() => new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(clock.value))
const dateText = computed(() => new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', weekday: 'long' }).format(clock.value))
const productionChart = computed(() => data.value ? productionOption(data.value) : {})
const qualityChart = computed(() => data.value ? qualityOption(data.value) : {})
const equipmentChart = computed(() => data.value ? equipmentOption(data.value) : {})
const equipmentTotal = computed(() => data.value ? data.value.equipment.available + data.value.equipment.fault + data.value.equipment.disabled : 0)
const completedInPeriod = computed(() => data.value?.trend.reduce((sum, point) => sum + point.completed, 0) || 0)
const filteredOrders = computed(() => (data.value?.workOrders || []).filter(order => {
  const match = `${order.workOrderNo} ${order.itemName || ''}`.toLowerCase().includes(search.value.trim().toLowerCase())
  return match && (orderFilter.value === 'all' || (orderFilter.value === 'overdue' && order.overdue) || (orderFilter.value === 'paused' && order.status === '5'))
}))

watch(() => data.value?.processes, processes => {
  if (!processes?.some(p => p.processId === selectedProcess.value)) selectedProcess.value = processes?.[0]?.processId ?? null
})
function number(value: number) { return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(value) }
function rate(value: number | null) { return value === null ? '—' : value.toFixed(1) }
function progress(order: ScreenWorkOrder) { return order.totalSteps ? Math.min(100, Math.round(order.completedSteps / order.totalSteps * 100)) : 0 }
function formatTime(value: string) { return value.replace('T', ' ').slice(11, 19) }
function shortDate(value: string | null) { return value ? value.replace('T', ' ').slice(5, 16) : '未设置' }
function elapsed(value: string) {
  const timestamp = Date.parse(value.replace(' ', 'T') + '+08:00')
  const minutes = Math.max(0, Math.floor((clock.value.getTime() - timestamp) / 60000))
  if (!Number.isFinite(minutes)) return '时间未知'
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  if (minutes < 1440) return `${Math.floor(minutes / 60)} 小时前`
  return `${Math.floor(minutes / 1440)} 天前`
}
function jumpTo(id: string) { document.getElementById(id)?.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start' }) }
function modulePath(keyword: DashboardModule) { return findModulePath(router.getRoutes(), keyword) }
function openModule(keyword: DashboardModule) {
  const path = modulePath(keyword)
  if (path) { orderDialog.value?.close(); void router.push(path) }
}
async function showOrder(order: ScreenWorkOrder) { selectedOrder.value = order; await nextTick(); orderDialog.value?.showModal() }
function onDialogClick(event: MouseEvent) {
  const dialog = orderDialog.value
  if (!dialog || event.target !== dialog) return
  const rect = dialog.getBoundingClientRect()
  if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) dialog.close()
}
async function toggleFullscreen() {
  try {
    if (document.fullscreenElement) await document.exitFullscreen()
    else if (screenElement.value?.requestFullscreen) await screenElement.value.requestFullscreen()
    else ElMessage.info('当前浏览器不支持全屏，请使用浏览器的全屏功能')
  } catch { ElMessage.warning('暂时无法进入全屏，请重试') }
}
function syncFullscreen() { isFullscreen.value = document.fullscreenElement === screenElement.value }
onMounted(() => document.addEventListener('fullscreenchange', syncFullscreen))
onBeforeUnmount(() => document.removeEventListener('fullscreenchange', syncFullscreen))
</script>

<style lang="scss" src="./screen.scss" />
