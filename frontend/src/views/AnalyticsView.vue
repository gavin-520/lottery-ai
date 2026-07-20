<template>
  <div class="page">
    <el-alert
      :title="`福彩3D 数据分析 · 共 ${totalPeriods} 期`"
      type="success"
      :closable="false"
      show-icon
      class="banner"
    />
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>号码出现频率（0–9）</template>
          <div ref="digitChartRef" class="chart" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>和值分布</template>
          <div ref="sumChartRef" class="chart" />
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="16" class="row2">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>百位频率</template>
          <div ref="pos1ChartRef" class="chart-sm" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>十位频率</template>
          <div ref="pos2ChartRef" class="chart-sm" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>个位频率</template>
          <div ref="pos3ChartRef" class="chart-sm" />
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="16" class="row2">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>跨度分布</template>
          <div ref="spanChartRef" class="chart-sm" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>奇偶形态分布</template>
          <div ref="oeChartRef" class="chart-sm" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { fetchFc3dAnalytics } from '@/api/fc3d'

const digitChartRef = ref<HTMLDivElement>()
const sumChartRef = ref<HTMLDivElement>()
const pos1ChartRef = ref<HTMLDivElement>()
const pos2ChartRef = ref<HTMLDivElement>()
const pos3ChartRef = ref<HTMLDivElement>()
const spanChartRef = ref<HTMLDivElement>()
const oeChartRef = ref<HTMLDivElement>()
const totalPeriods = ref(0)

const charts: echarts.ECharts[] = []

function renderBar(el: HTMLDivElement | undefined, labels: string[], values: number[], color: string) {
  if (!el) return
  const chart = echarts.init(el)
  charts.push(chart)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 16, bottom: 36, top: 24 },
    xAxis: { type: 'category', data: labels },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: values, itemStyle: { color } }],
  })
}

async function loadData() {
  try {
    const data = await fetchFc3dAnalytics()
    totalPeriods.value = data.totalPeriods
    renderBar(
      digitChartRef.value,
      data.digitFrequency.map((i) => String(i.ball)),
      data.digitFrequency.map((i) => i.count),
      '#27ae60'
    )
    const sumKeys = Object.keys(data.sumDistribution)
    renderBar(sumChartRef.value, sumKeys, sumKeys.map((k) => data.sumDistribution[k]), '#e67e22')
    renderBar(
      pos1ChartRef.value,
      data.pos1Frequency.map((i) => String(i.ball)),
      data.pos1Frequency.map((i) => i.count),
      '#c0392b'
    )
    renderBar(
      pos2ChartRef.value,
      data.pos2Frequency.map((i) => String(i.ball)),
      data.pos2Frequency.map((i) => i.count),
      '#2980b9'
    )
    renderBar(
      pos3ChartRef.value,
      data.pos3Frequency.map((i) => String(i.ball)),
      data.pos3Frequency.map((i) => i.count),
      '#8e44ad'
    )
    const spanKeys = Object.keys(data.spanDistribution)
    renderBar(spanChartRef.value, spanKeys, spanKeys.map((k) => data.spanDistribution[k]), '#16a085')
    const oeKeys = Object.keys(data.oddEvenDistribution)
    renderBar(oeChartRef.value, oeKeys, oeKeys.map((k) => data.oddEvenDistribution[k]), '#2c3e50')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载失败')
  }
}

function handleResize() {
  charts.forEach((c) => c.resize())
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  charts.forEach((c) => c.dispose())
})
</script>

<style scoped>
.page { padding: 4px; }
.banner { margin-bottom: 12px; }
.row2 { margin-top: 16px; }
.chart { height: 320px; }
.chart-sm { height: 260px; }
</style>
