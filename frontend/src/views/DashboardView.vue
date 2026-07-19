<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>服务状态</template>
          <div class="status-item">
            <span>Backend</span>
            <el-tag :type="backendOk ? 'success' : 'danger'">{{ backendOk ? 'UP' : 'DOWN' }}</el-tag>
          </div>
          <div class="status-item">
            <span>AI Service</span>
            <el-tag :type="aiOk ? 'success' : 'danger'">{{ aiOk ? 'OK' : 'DOWN' }}</el-tag>
          </div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="never">
          <template #header>历史数据</template>
          <div class="stat-value">{{ historyTotal }}</div>
          <p class="stat-label">已入库期数</p>
          <el-button type="primary" link @click="$router.push('/history')">查看全部 →</el-button>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="never">
          <template #header>Mock 预测</template>
          <el-button type="primary" :loading="predictLoading" @click="runPredict">生成预测</el-button>
          <div v-if="prediction" class="prediction">
            <p>红球：{{ prediction.red_balls.join(', ') }}</p>
            <p>蓝球：{{ prediction.blue_ball }}</p>
            <p class="note">{{ prediction.note }}</p>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import axios from 'axios'
import { fetchHistory } from '@/api/history'

const backendOk = ref(false)
const aiOk = ref(false)
const historyTotal = ref(0)
const predictLoading = ref(false)
const prediction = ref<{
  red_balls: number[]
  blue_ball: number
  note: string
} | null>(null)

const aiBaseUrl = import.meta.env.VITE_AI_BASE_URL || 'http://localhost:8000'

async function checkHealth() {
  try {
    const base = import.meta.env.VITE_API_BASE_URL || ''
    await axios.get(`${base}/api/v1/health`, { timeout: 5000 })
    backendOk.value = true
  } catch {
    backendOk.value = false
  }

  try {
    await axios.get(`${aiBaseUrl}/health`, { timeout: 5000 })
    aiOk.value = true
  } catch {
    aiOk.value = false
  }
}

async function loadHistoryCount() {
  try {
    const page = await fetchHistory(1, 1)
    historyTotal.value = page.total
  } catch {
    historyTotal.value = 0
  }
}

async function runPredict() {
  predictLoading.value = true
  try {
    const res = await axios.post(`${aiBaseUrl}/api/v1/predict`, { period: 'next' })
    prediction.value = res.data
  } catch {
    prediction.value = null
  } finally {
    predictLoading.value = false
  }
}

onMounted(() => {
  checkHealth()
  loadHistoryCount()
})
</script>

<style scoped>
.dashboard {
  padding: 4px;
}

.status-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
}

.stat-value {
  font-size: 36px;
  font-weight: 600;
  color: #409eff;
}

.stat-label {
  color: #909399;
  margin: 4px 0 12px;
}

.prediction {
  margin-top: 12px;
  font-size: 14px;
}

.note {
  color: #909399;
  font-size: 12px;
  margin-top: 8px;
}
</style>
