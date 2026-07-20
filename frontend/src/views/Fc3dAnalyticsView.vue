<template>
  <div class="page">
    <el-alert
      :title="`福彩3D 深度分析 · 位频率 / 遗漏 / 和值 / 奇偶 / AI 统计解读 · 共 ${totalPeriods} 期`"
      type="success"
      :closable="false"
      show-icon
      class="banner"
    />

    <!-- 位频率 -->
    <el-card shadow="never" class="section">
      <template #header>位频率（百位 / 十位 / 个位）</template>
      <el-row :gutter="16">
        <el-col :span="8">
          <div class="chart-title">百位 0–9</div>
          <div ref="hundredsChartRef" class="chart-sm" />
        </el-col>
        <el-col :span="8">
          <div class="chart-title">十位 0–9</div>
          <div ref="tensChartRef" class="chart-sm" />
        </el-col>
        <el-col :span="8">
          <div class="chart-title">个位 0–9</div>
          <div ref="unitsChartRef" class="chart-sm" />
        </el-col>
      </el-row>
    </el-card>

    <!-- 遗漏分析 -->
    <el-card shadow="never" class="section">
      <template #header>遗漏分析（Top 15，按遗漏期数降序）</template>
      <el-table :data="topMissing" size="small" stripe>
        <el-table-column prop="position" label="位置" width="120">
          <template #default="{ row }">{{ positionLabel(row.position) }}</template>
        </el-table-column>
        <el-table-column prop="number" label="数字" width="100" />
        <el-table-column prop="missing" label="遗漏期数" />
      </el-table>
    </el-card>

    <!-- 和值分析 -->
    <el-card shadow="never" class="section">
      <template #header>和值分析</template>
      <p class="metric">历史平均和值：<strong>{{ sumAnalysis?.average ?? '-' }}</strong></p>
      <el-row :gutter="16">
        <el-col :span="12">
          <div class="chart-title">和值分布</div>
          <div ref="sumDistChartRef" class="chart-sm" />
        </el-col>
        <el-col :span="12">
          <div class="chart-title">近期和值走势</div>
          <div ref="sumTrendChartRef" class="chart-sm" />
        </el-col>
      </el-row>
    </el-card>

    <!-- 奇偶分析 -->
    <el-card shadow="never" class="section">
      <template #header>奇偶分析</template>
      <el-row :gutter="16" align="middle">
        <el-col :span="12">
          <p class="metric">
            最新一期（{{ oddEven?.issue ?? '-' }}）：
            奇数 {{ oddEven?.oddCount ?? '-' }} 个 · 偶数 {{ oddEven?.evenCount ?? '-' }} 个 ·
            形态 <el-tag type="warning">{{ oddEven?.pattern ?? '-' }}</el-tag>
          </p>
        </el-col>
        <el-col :span="12">
          <div class="chart-title">历史奇偶形态分布</div>
          <div ref="oddEvenChartRef" class="chart-sm" />
        </el-col>
      </el-row>
    </el-card>

    <!-- AI 分析 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <span>AI 统计解读</span>
          <el-button type="primary" :loading="analyzing" @click="runAnalyze">生成 AI 统计解读</el-button>
        </div>
      </template>

      <el-alert
        title="统计分析与概率模型解释，不构成中奖承诺或购彩建议"
        type="warning"
        :closable="false"
        show-icon
        class="disclaimer"
      />

      <div v-if="analysis">
        <h4>特征摘要</h4>
        <ul class="notes">
          <li v-for="(note, idx) in analysis.features.notes" :key="idx">{{ note }}</li>
        </ul>
        <p class="metric">
          热号：百位 {{ formatDigits(analysis.features.hotDigits.hundreds) }} · 十位
          {{ formatDigits(analysis.features.hotDigits.tens) }} · 个位
          {{ formatDigits(analysis.features.hotDigits.units) }}
        </p>
        <p class="metric">
          和值均值：{{ analysis.features.sumAverage ?? '-' }} · 主导奇偶形态：
          {{ analysis.features.dominantOddEven ?? '-' }}
        </p>

        <h4>候选解读</h4>
        <el-table :data="analysis.candidateAnalysis" size="small" stripe>
          <el-table-column prop="number" label="号码" width="100" />
          <el-table-column prop="score" label="评分" width="90" />
          <el-table-column label="契合信号" width="200">
            <template #default="{ row }">
              <el-tag
                v-for="(signal, idx) in row.alignedSignals"
                :key="idx"
                size="small"
                type="success"
                class="tag-gap"
              >
                {{ signal }}
              </el-tag>
              <span v-if="!row.alignedSignals.length">-</span>
            </template>
          </el-table-column>
          <el-table-column label="风险提示" width="220">
            <template #default="{ row }">
              <el-tag
                v-for="(risk, idx) in row.riskFlags"
                :key="idx"
                size="small"
                type="danger"
                class="tag-gap"
              >
                {{ risk }}
              </el-tag>
              <span v-if="!row.riskFlags.length">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="comment" label="统计解读" />
        </el-table>

        <h4>综合建议</h4>
        <p class="metric">
          统计首选：<el-tag type="danger">{{ analysis.recommendation.preferred ?? '-' }}</el-tag>
          · 置信度 {{ (analysis.confidence * 100).toFixed(0) }}%
        </p>
        <ul class="notes">
          <li v-for="(reason, idx) in analysis.recommendation.rationale" :key="idx">{{ reason }}</li>
        </ul>
        <el-alert
          :title="analysis.recommendation.disclaimer"
          type="warning"
          :closable="false"
          show-icon
          class="disclaimer"
        />
        <p class="model-note">模型：{{ analysis.modelName }}</p>
      </div>
      <el-empty v-else description="点击「生成 AI 统计解读」查看统计分析结果" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import {
  analyzeFc3d,
  fetchFc3dFrequency,
  fetchFc3dMissing,
  fetchFc3dOddEven,
  fetchFc3dSumAnalysis,
  type Fc3dAnalyzeResponse,
  type Fc3dMissingItem,
  type Fc3dOddEvenResponse,
  type Fc3dSumAnalysisResponse,
} from '@/api/fc3d'

const totalPeriods = ref(0)
const topMissing = ref<Fc3dMissingItem[]>([])
const sumAnalysis = ref<Fc3dSumAnalysisResponse | null>(null)
const oddEven = ref<Fc3dOddEvenResponse | null>(null)
const analysis = ref<Fc3dAnalyzeResponse | null>(null)
const analyzing = ref(false)

const hundredsChartRef = ref<HTMLDivElement>()
const tensChartRef = ref<HTMLDivElement>()
const unitsChartRef = ref<HTMLDivElement>()
const sumDistChartRef = ref<HTMLDivElement>()
const sumTrendChartRef = ref<HTMLDivElement>()
const oddEvenChartRef = ref<HTMLDivElement>()

const charts: echarts.ECharts[] = []

const POSITION_LABELS: Record<string, string> = {
  hundreds: '百位',
  tens: '十位',
  units: '个位',
}

function positionLabel(position: string) {
  return POSITION_LABELS[position] ?? position
}

function formatDigits(digits: number[] | undefined) {
  return digits && digits.length ? digits.join('、') : '-'
}

function renderBar(el: HTMLDivElement | undefined, labels: string[], values: number[], color: string) {
  if (!el) return
  const chart = echarts.init(el)
  charts.push(chart)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 36, right: 12, bottom: 30, top: 16 },
    xAxis: { type: 'category', data: labels },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: values, itemStyle: { color } }],
  })
}

function renderLine(el: HTMLDivElement | undefined, labels: string[], values: number[], color: string) {
  if (!el) return
  const chart = echarts.init(el)
  charts.push(chart)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 36, right: 12, bottom: 30, top: 16 },
    xAxis: { type: 'category', data: labels },
    yAxis: { type: 'value' },
    series: [{ type: 'line', data: values, smooth: true, itemStyle: { color }, lineStyle: { color } }],
  })
}

function sortNumericKeys(record: Record<string, number>) {
  return Object.keys(record).sort((a, b) => Number(a) - Number(b))
}

async function loadFrequency() {
  const data = await fetchFc3dFrequency()
  totalPeriods.value = data.totalPeriods
  const hKeys = sortNumericKeys(data.hundreds)
  const tKeys = sortNumericKeys(data.tens)
  const uKeys = sortNumericKeys(data.units)
  renderBar(hundredsChartRef.value, hKeys, hKeys.map((k) => data.hundreds[k]), '#c0392b')
  renderBar(tensChartRef.value, tKeys, tKeys.map((k) => data.tens[k]), '#2980b9')
  renderBar(unitsChartRef.value, uKeys, uKeys.map((k) => data.units[k]), '#8e44ad')

  const oeKeys = Object.keys(data.oddEvenDistribution)
  renderBar(oddEvenChartRef.value, oeKeys, oeKeys.map((k) => data.oddEvenDistribution[k]), '#2c3e50')
}

async function loadMissing() {
  const data = await fetchFc3dMissing()
  topMissing.value = [...data.items].sort((a, b) => b.missing - a.missing).slice(0, 15)
}

async function loadSum() {
  const data = await fetchFc3dSumAnalysis()
  sumAnalysis.value = data
  const distKeys = sortNumericKeys(data.distribution)
  renderBar(sumDistChartRef.value, distKeys, distKeys.map((k) => data.distribution[k]), '#e67e22')
  renderLine(
    sumTrendChartRef.value,
    data.recentTrend.map((t) => t.issue),
    data.recentTrend.map((t) => t.sum),
    '#16a085'
  )
}

async function loadOddEven() {
  oddEven.value = await fetchFc3dOddEven()
}

async function runAnalyze() {
  analyzing.value = true
  try {
    analysis.value = await analyzeFc3d()
    ElMessage.success('AI 统计解读生成完成')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : 'AI 统计解读失败')
  } finally {
    analyzing.value = false
  }
}

function handleResize() {
  charts.forEach((c) => c.resize())
}

onMounted(async () => {
  try {
    await Promise.all([loadFrequency(), loadMissing(), loadSum(), loadOddEven()])
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载失败')
  }
  await nextTick()
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
.section { margin-bottom: 16px; }
.chart-title { font-size: 13px; color: #666; margin-bottom: 4px; }
.chart-sm { height: 220px; }
.metric { line-height: 2; }
.notes { margin: 8px 0; padding-left: 20px; line-height: 1.9; color: #555; }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.disclaimer { margin: 12px 0; }
.tag-gap { margin: 2px 4px 2px 0; }
.model-note { color: #999; font-size: 12px; margin-top: 8px; }
</style>
