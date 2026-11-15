import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { getDashboardScreen, type DashboardScreen } from '@/api/dashboard'

export function useProductionScreen() {
  const days = ref(7)
  const workUnitId = ref('')
  const data = shallowRef<DashboardScreen | null>(null)
  const appliedQuery = shallowRef<{ days: number; workUnitId: string } | null>(null)
  const filtersPending = computed(() => appliedQuery.value !== null &&
    (appliedQuery.value.days !== days.value || appliedQuery.value.workUnitId !== workUnitId.value))
  const loading = ref(false)
  const error = ref('')
  const autoRefresh = ref(true)
  const countdown = ref(30)
  const clock = ref(new Date())
  let sequence = 0
  let disposed = false
  let timer: number | undefined
  let lastSuccess = 0

  async function refresh() {
    const current = ++sequence
    const query = { days: days.value, workUnitId: workUnitId.value }
    loading.value = true
    try {
      const response = await getDashboardScreen({
        days: query.days,
        ...(query.workUnitId ? { workUnitId: Number(query.workUnitId) } : {})
      })
      // Filters can change while requests are in flight. Only the latest response wins.
      if (disposed || current !== sequence) return
      data.value = response
      appliedQuery.value = query
      error.value = ''
      lastSuccess = Date.now()
    } catch (cause) {
      if (disposed || current !== sequence) return
      error.value = cause instanceof Error ? cause.message : '暂时无法更新数据'
    } finally {
      if (!disposed && current === sequence) {
        loading.value = false
        countdown.value = 30
      }
    }
  }

  function onVisible() {
    if (!document.hidden && autoRefresh.value && !loading.value && Date.now() - lastSuccess >= 30000) void refresh()
  }

  watch([days, workUnitId], () => void refresh())
  watch(autoRefresh, () => { countdown.value = 30 })
  onMounted(() => {
    void refresh()
    timer = window.setInterval(() => {
      clock.value = new Date()
      if (!autoRefresh.value || document.hidden || loading.value) return
      countdown.value -= 1
      if (countdown.value <= 0) void refresh()
    }, 1000)
    document.addEventListener('visibilitychange', onVisible)
  })
  onBeforeUnmount(() => {
    disposed = true
    sequence += 1
    window.clearInterval(timer)
    document.removeEventListener('visibilitychange', onVisible)
  })

  return { days, workUnitId, data, appliedQuery, filtersPending, loading, error, autoRefresh, countdown, clock, refresh }
}
