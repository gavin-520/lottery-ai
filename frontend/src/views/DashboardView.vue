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
            <el-tag :type="aiOk ? (aiDegraded ? 'warning' : 'success') : 'danger'">
              {{ aiOk ? (aiDegraded ? 'DEGRADED' : 'OK') : 'DOWN' }}
            </el-tag>
          </div>
          <div v-if="recentBreaches > 0" class="status-item">
            <span>SLA 告警 (会话)</span>
            <el-tag type="danger">{{ recentBreaches }}</el-tag>
          </div>
          <div class="status-item">
            <span>SSE</span>
            <el-tag :type="sseConnected ? 'success' : 'info'">{{ sseConnected ? 'LIVE' : 'OFF' }}</el-tag>
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
          <template #header>数据同步</template>
          <div v-if="syncStatus">
            <p>来源：{{ syncStatus.source }}</p>
            <p>状态：
              <el-tag size="small" :type="syncStatus.status === 'FAILED' ? 'danger' : syncStatus.status === 'SUCCESS' ? 'success' : 'info'">
                {{ syncStatus.status }}
              </el-tag>
            </p>
            <p>新增：{{ syncStatus.newCount }} / {{ syncStatus.fetchedCount }}</p>
            <p class="sync-msg">{{ syncStatus.message }}</p>
          </div>
          <el-button v-if="auth.isAdmin" type="primary" link @click="doSync">手动同步 →</el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="row2">
      <el-col :span="24">
        <el-card shadow="never">
          <template #header>快捷入口</template>
          <el-space wrap>
            <el-button type="primary" link @click="$router.push('/predict')">智能预测</el-button>
            <el-button type="primary" link @click="$router.push('/models')">模型对比</el-button>
            <el-button type="primary" link @click="$router.push('/agent')">AI 助手</el-button>
            <el-button type="primary" link @click="$router.push('/workflow')">多 Agent 工作流</el-button>
            <el-button type="primary" link @click="$router.push('/backtest')">运行回测</el-button>
            <el-button type="primary" link @click="$router.push('/analytics')">数据分析</el-button>
          </el-space>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { fetchHistory } from '@/api/history'
import { fetchSyncStatus, subscribeEvents, triggerSync, type SyncStatus } from '@/api/sync'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const backendOk = ref(false)
const aiOk = ref(false)
const aiDegraded = ref(false)
const recentBreaches = ref(0)
const historyTotal = ref(0)
const syncStatus = ref<SyncStatus | null>(null)
const sseConnected = ref(false)
let eventSource: EventSource | null = null

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
    const res = await axios.get(`${aiBaseUrl}/health`, { timeout: 5000 })
    aiOk.value = true
    aiDegraded.value = Boolean(res.data?.kafka?.degraded)
  } catch {
    aiOk.value = false
    aiDegraded.value = false
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

async function loadSyncStatus() {
  try {
    syncStatus.value = await fetchSyncStatus()
  } catch {
    syncStatus.value = null
  }
}

async function doSync() {
  try {
    syncStatus.value = await triggerSync()
    await loadHistoryCount()
    if (syncStatus.value.status === 'FAILED') {
      ElMessage.error(syncStatus.value.message || '同步失败')
    } else {
      ElMessage.success('同步完成')
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '同步失败')
  }
}

function connectSse() {
  if (!auth.token) return
  eventSource = subscribeEvents(auth.token, (name, data) => {
    sseConnected.value = true
    if (name === 'sync-failed') {
      ElMessage.error('数据同步失败')
      aiDegraded.value = true
      loadSyncStatus()
      return
    }
    if (name === 'sla-breach') {
      recentBreaches.value += 1
      ElMessage.warning('SLA SLO 告警')
      return
    }
    if (data && typeof data === 'object' && 'newCount' in data) {
      loadSyncStatus()
      loadHistoryCount()
    }
  })
  eventSource.onopen = () => { sseConnected.value = true }
  eventSource.onerror = () => { sseConnected.value = false }
}

onMounted(() => {
  checkHealth()
  loadHistoryCount()
  loadSyncStatus()
  connectSse()
})

onUnmounted(() => {
  eventSource?.close()
})
</script>

<style scoped>
.dashboard { padding: 4px; }
.row2 { margin-top: 20px; }
.status-item {
  display: flex; justify-content: space-between; align-items: center; padding: 8px 0;
}
.stat-value { font-size: 36px; font-weight: 600; color: #409eff; }
.stat-label { color: #909399; margin: 4px 0 12px; }
.sync-msg { color: #909399; font-size: 12px; margin-top: 8px; }
</style>
