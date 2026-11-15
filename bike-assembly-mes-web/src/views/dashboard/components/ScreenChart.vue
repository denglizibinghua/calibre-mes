<template>
  <div ref="element" class="screen-chart" role="img" :aria-label="label" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use, type EChartsCoreOption, type EChartsType } from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { AriaComponent, GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([BarChart, LineChart, PieChart, AriaComponent, GridComponent, TooltipComponent, CanvasRenderer])
const props = defineProps<{ option: EChartsCoreOption; label: string }>()
const element = ref<HTMLDivElement>()
let chart: EChartsType | undefined
let observer: ResizeObserver | undefined
const render = () => chart?.setOption({
  ...props.option,
  animation: !window.matchMedia('(prefers-reduced-motion: reduce)').matches,
  aria: { enabled: true }
}, { notMerge: true })

onMounted(() => {
  if (!element.value) return
  chart = init(element.value, undefined, { renderer: 'canvas' })
  render()
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(element.value)
})
watch(() => props.option, render, { deep: true })
onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
  chart = undefined
})
</script>

<style scoped>
.screen-chart { width: 100%; min-width: 0; height: 100%; min-height: 160px; }
</style>
