<template>
  <div class="page">
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>SLA 概览 (24h)</span>
              <el-tag v-if="summary && summary.successRate < 95" type="danger" size="small">SLO 告警</el-tag>
            </div>
          </template>
          <el-descriptions v-if="summary" :column="1" border>
            <el-descriptions-item label="区域">{{ summary.region }}</el-descriptions-item>
            <el-descriptions-item label="总调用">{{ summary.totalCalls }}</el-descriptions-item>
            <el-descriptions-item label="成功率">{{ summary.successRate.toFixed(1) }}%</el-descriptions-item>
            <el-descriptions-item label="平均延迟">{{ summary.avgLatencyMs.toFixed(0) }} ms</el-descriptions-item>
            <el-descriptions-item label="P95 延迟">{{ summary.p95LatencyMs.toFixed(0) }} ms</el-descriptions-item>
            <el-descriptions-item label="失败次数">{{ summary.failedCalls }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>SLO  breach 记录</span>
              <el-button @click="load">刷新</el-button>
            </div>
          </template>
          <el-table v-loading="loading" :data="breaches" stripe>
            <el-table-column prop="metric" label="指标" width="120" />
            <el-table-column prop="thresholdValue" label="阈值" width="90" />
            <el-table-column prop="actualValue" label="实际值" width="90" />
            <el-table-column prop="region" label="区域" width="100" />
            <el-table-column prop="message" label="说明" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="时间" width="180" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="row2">
      <el-col :span="24">
        <el-card shadow="never">
          <template #header>外部 API 调用日志</template>
          <el-table v-loading="loading" :data="logs" stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="provider" label="Provider" width="120" />
            <el-table-column prop="latencyMs" label="延迟(ms)" width="100" />
            <el-table-column prop="httpStatus" label="HTTP" width="80" />
            <el-table-column label="结果" width="90">
              <template #default="{ row }">
                <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                  {{ row.success ? 'OK' : 'FAIL' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="errorType" label="错误类型" width="120" />
            <el-table-column prop="region" label="区域" width="100" />
            <el-table-column prop="createdAt" label="时间" width="180" />
            <el-table-column prop="endpoint" label="Endpoint" show-overflow-tooltip />
          </el-table>
          <div class="pagination">
            <el-pagination v-model:current-page="page" :total="total" layout="total, prev, pager, next" @current-change="load" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getSlaSummary, listSlaBreaches, listSlaLogs, type SlaBreach, type SlaLog, type SlaSummary } from '@/api/sla'
import { subscribeEvents } from '@/api/sync'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const summary = ref<SlaSummary | null>(null)
const logs = ref<SlaLog[]>([])
const breaches = ref<SlaBreach[]>([])
const page = ref(1)
const total = ref(0)
let eventSource: EventSource | null = null

async function load() {
  loading.value = true
  try {
    const [s, b, l] = await Promise.all([
      getSlaSummary(24),
      listSlaBreaches(1, 20),
      listSlaLogs(page.value)
    ])
    summary.value = s
    breaches.value = b.records
    logs.value = l.records
    total.value = l.total
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  if (auth.token) {
    eventSource = subscribeEvents(auth.token, (name) => {
      if (name === 'sla-breach') load()
    })
  }
})

onUnmounted(() => eventSource?.close())
</script>

<style scoped>
.page { padding: 4px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
.row2 { margin-top: 20px; }
</style>
