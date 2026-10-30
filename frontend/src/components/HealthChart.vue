<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'

/**
 * ECharts 就位检查组件（ENG-007 任务①）。
 *
 * ★ 本文件是 **ECharts 真实引用**的落点（`import * as echarts from 'echarts'`）。
 * ★ 画的是一张空壳折线图（占位数据），只为证明"图表栈能起"；
 *   真正的统计图（掌握度曲线、日活趋势 …）属后续编码单（API-42 等）。
 */
const el = ref(null)
let chart = null

onMounted(() => {
  chart = echarts.init(el.value)
  chart.setOption({
    title: { text: '掌握度趋势（占位）', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: ['D1', 'D2', 'D4', 'D7', 'D15', 'D30'] },
    yAxis: { type: 'value', min: 0, max: 100 },
    series: [{ type: 'line', smooth: true, data: [0, 0, 0, 0, 0, 0] }]
  })
})

onBeforeUnmount(() => {
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="el" style="width: 100%; height: 260px"></div>
</template>
