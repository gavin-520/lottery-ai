<template>
  <div class="page">
    <el-row :gutter="20">
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>红球出现频率</template>
          <div ref="redChartRef" class="chart" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>蓝球出现频率</template>
          <div ref="blueChartRef" class="chart" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { fetchFrequency } from '@/api/analytics'

const redChartRef = ref<HTMLDivElement>()
const blueChartRef = ref<HTMLDivElement>()
let redChart: echarts.ECharts | null = null
let blueChart: echarts.ECharts | null = null

function renderBar(chart: echarts.ECharts, title: string, balls: number[], counts: number[], color: string) {
  chart.setOption({
    title: { text: title, left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, bottom: 40, top: 40 },
    xAxis: { type: 'category', data: balls.map(String) },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: counts, itemStyle: { color } }]
  })
}

async function loadData() {
  try {
    const data = await fetchFrequency()
    if (redChartRef.value) {
      redChart = echarts.init(redChartRef.value)
      renderBar(
        redChart,
        `共 ${data.totalPeriods} 期`,
        data.redFrequency.map((i) => i.ball),
        data.redFrequency.map((i) => i.count),
        '#f56c6c'
      )
    }
    if (blueChartRef.value) {
      blueChart = echarts.init(blueChartRef.value)
      renderBar(
        blueChart,
        `共 ${data.totalPeriods} 期`,
        data.blueFrequency.map((i) => i.ball),
        data.blueFrequency.map((i) => i.count),
        '#409eff'
      )
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载失败')
  }
}

function handleResize() {
  redChart?.resize()
  blueChart?.resize()
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  redChart?.dispose()
  blueChart?.dispose()
})
</script>

<style scoped>
.page {
  padding: 4px;
}

.chart {
  height: 360px;
}
</style>
