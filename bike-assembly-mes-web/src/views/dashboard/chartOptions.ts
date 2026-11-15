import type { EChartsCoreOption } from 'echarts/core'
import type { DashboardScreen } from '@/api/dashboard'

const ink = '#8b857a'
const gold = '#c6a96e'
const jade = '#55a98f'
const grid = 'rgba(198, 169, 110, 0.14)'
const font = 'Bahnschrift, "Noto Sans SC", "Microsoft YaHei", sans-serif'
const tooltip = {
  trigger: 'axis',
  backgroundColor: '#0f141c',
  borderColor: 'rgba(198, 169, 110, 0.28)',
  textStyle: { color: '#ede8dc', fontFamily: font, fontSize: 11 },
  extraCssText: 'box-shadow: 0 16px 40px rgba(0,0,0,.45); border-radius: 2px;'
}

export function productionOption(data: DashboardScreen): EChartsCoreOption {
  return {
    color: [gold, jade],
    textStyle: { fontFamily: font, color: ink },
    tooltip,
    grid: { left: 38, right: 16, top: 18, bottom: 30 },
    xAxis: {
      type: 'category',
      boundaryGap: true,
      data: data.trend.map(p => p.date.slice(5).replace('-', '/')),
      axisLine: { lineStyle: { color: grid } },
      axisTick: { show: false },
      axisLabel: { color: ink, fontSize: 10 }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      min: 0,
      max: (range: { max: number }) => Math.max(1, range.max),
      splitLine: { lineStyle: { color: grid, type: 'dashed' } },
      axisLabel: { color: ink, fontSize: 10 }
    },
    series: [
      {
        name: '实际完工（单）',
        type: 'bar',
        data: data.trend.map(p => p.completed),
        barMaxWidth: 16,
        itemStyle: { color: gold, borderRadius: [1, 1, 0, 0] }
      },
      {
        name: '计划完工（单）',
        type: 'line',
        data: data.trend.map(p => p.planned),
        symbol: 'circle',
        symbolSize: 4,
        lineStyle: { color: jade, width: 1.5, type: 'dashed' },
        itemStyle: { color: jade }
      }
    ]
  }
}

export function qualityOption(data: DashboardScreen): EChartsCoreOption {
  return {
    tooltip: { ...tooltip, valueFormatter: (value: number | string) => `${value}%` },
    grid: { left: 43, right: 16, top: 16, bottom: 28 },
    textStyle: { fontFamily: font, color: ink },
    xAxis: {
      type: 'category',
      data: data.trend.map(p => p.date.slice(5).replace('-', '/')),
      axisLine: { lineStyle: { color: grid } },
      axisTick: { show: false },
      axisLabel: { color: ink, fontSize: 9, interval: 'auto' }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      interval: 50,
      axisLabel: { color: ink, fontSize: 9, formatter: '{value}%' },
      splitLine: { lineStyle: { color: grid, type: 'dashed' } }
    },
    series: [{
      name: '初检合格率',
      type: 'line',
      connectNulls: false,
      smooth: false,
      symbolSize: 5,
      data: data.trend.map(p => p.inspected ? Number((p.passed / p.inspected * 100).toFixed(1)) : null),
      lineStyle: { color: jade, width: 2 },
      itemStyle: { color: jade },
      areaStyle: { color: 'rgba(85, 169, 143, 0.10)' }
    }]
  }
}

export function equipmentOption(data: DashboardScreen): EChartsCoreOption {
  const e = data.equipment
  return {
    tooltip: {
      trigger: 'item',
      backgroundColor: '#0f141c',
      borderColor: 'rgba(198, 169, 110, 0.28)',
      textStyle: { color: '#ede8dc', fontFamily: font, fontSize: 11 },
      formatter: '{b}: {c} 台'
    },
    series: [{
      type: 'pie',
      radius: ['72%', '88%'],
      center: ['50%', '50%'],
      avoidLabelOverlap: true,
      label: { show: false },
      emphasis: { scale: false },
      data: [
        { value: e.available, name: '可用', itemStyle: { color: jade } },
        { value: e.fault, name: '故障 / 维修', itemStyle: { color: '#c98a4b' } },
        { value: e.disabled, name: '停用', itemStyle: { color: '#3a414c' } }
      ],
      emptyCircleStyle: { color: grid },
      itemStyle: { borderColor: '#0c1118', borderWidth: 4 }
    }]
  }
}
