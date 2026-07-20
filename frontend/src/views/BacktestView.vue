<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>福彩3D 回测</span>
          <el-button type="primary" :loading="loading" @click="run">运行回测</el-button>
        </div>
      </template>

      <el-form inline>
        <el-form-item label="最小训练期数">
          <el-input-number v-model="minHistory" :min="5" :max="50" />
        </el-form-item>
      </el-form>

      <el-descriptions v-if="result" :column="2" border class="result">
        <el-descriptions-item label="彩种">{{ result.lotteryType }}</el-descriptions-item>
        <el-descriptions-item label="回测期数">{{ result.totalPeriods }}</el-descriptions-item>
        <el-descriptions-item label="直选命中率">{{ (result.exactHitRate * 100).toFixed(2) }}%</el-descriptions-item>
        <el-descriptions-item label="位号命中率">{{ (result.digitHitRate * 100).toFixed(2) }}%</el-descriptions-item>
        <el-descriptions-item label="平均命中位数">{{ result.avgDigitHits.toFixed(2) }} / 3</el-descriptions-item>
        <el-descriptions-item label="最大单期命中">{{ result.maxDigitHits }}</el-descriptions-item>
        <el-descriptions-item label="说明" :span="2">{{ result.note }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import http, { type ApiResult } from '@/api/http'

interface Fc3dBacktestResult {
  lotteryType: string
  totalPeriods: number
  exactHitRate: number
  digitHitRate: number
  avgDigitHits: number
  maxDigitHits: number
  note: string
}

const loading = ref(false)
const result = ref<Fc3dBacktestResult | null>(null)
const minHistory = ref(10)

async function run() {
  loading.value = true
  try {
    const res = await http.post<ApiResult<Fc3dBacktestResult>>('/api/v1/fc3d/backtest/run', null, {
      params: { minHistory: minHistory.value },
    })
    if (res.data.code !== 200) throw new Error(res.data.message)
    result.value = res.data.data
    ElMessage.success('福彩3D 回测完成')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '回测失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page { padding: 4px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.result { margin-top: 20px; }
</style>
