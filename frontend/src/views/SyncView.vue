<template>
  <div class="page">
    <el-row :gutter="20" class="row">
      <el-col :span="24">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>福彩3D 每日同步</span>
              <el-space>
                <el-button type="success" :loading="fc3dLoading" @click="syncFc3d(false)">同步最新</el-button>
                <el-button :loading="fc3dLoading" @click="syncFc3d(true)">全量补齐</el-button>
                <el-button @click="loadFc3dStatus">刷新状态</el-button>
              </el-space>
            </div>
          </template>
          <el-descriptions v-if="fc3dStatus" :column="3" border>
            <el-descriptions-item label="调度">
              {{ fc3dStatus.schedulerEnabled ? '开启' : '关闭' }}
            </el-descriptions-item>
            <el-descriptions-item label="晚间 Cron">{{ fc3dStatus.cron }}</el-descriptions-item>
            <el-descriptions-item label="时区">{{ fc3dStatus.zone }}</el-descriptions-item>
            <el-descriptions-item label="最近状态">
              <el-tag :type="fc3dStatus.status === 'SUCCESS' ? 'success' : fc3dStatus.status === 'FAILED' ? 'danger' : 'info'" size="small">
                {{ fc3dStatus.status }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="库内期数">{{ fc3dStatus.totalInDb }}</el-descriptions-item>
            <el-descriptions-item label="最新期号">{{ fc3dStatus.latestIssue || '-' }}</el-descriptions-item>
            <el-descriptions-item label="上次新增">{{ fc3dStatus.newCount }}</el-descriptions-item>
            <el-descriptions-item label="完成时间">{{ fc3dStatus.finishedAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="消息">{{ fc3dStatus.message || '-' }}</el-descriptions-item>
          </el-descriptions>
          <p class="hint">默认每天 21:40（开奖后）与 08:10（补漏）自动拉取最新开奖并入库。</p>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="row">
      <el-col :span="24">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>同步历史</span>
              <el-space>
                <el-select v-model="statusFilter" clearable placeholder="状态" style="width: 120px" @change="load">
                  <el-option label="SUCCESS" value="SUCCESS" />
                  <el-option label="FAILED" value="FAILED" />
                  <el-option label="RUNNING" value="RUNNING" />
                </el-select>
                <el-button type="primary" :loading="retryLoading" @click="retry">重试同步</el-button>
                <el-button @click="load">刷新</el-button>
              </el-space>
            </div>
          </template>
          <el-table v-loading="loading" :data="logs" stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'SUCCESS' ? 'success' : row.status === 'FAILED' ? 'danger' : 'info'" size="small">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="source" label="来源" width="120" />
            <el-table-column prop="newCount" label="新增" width="70" />
            <el-table-column prop="region" label="区域" width="100" />
            <el-table-column prop="errorType" label="错误" width="110" />
            <el-table-column prop="parentLogId" label="重试来源" width="90" />
            <el-table-column prop="correlationId" label="Correlation ID" show-overflow-tooltip />
            <el-table-column prop="finishedAt" label="完成时间" width="180" />
            <el-table-column label="追踪" width="140">
              <template #default="{ row }">
                <el-button v-if="row.correlationId" link type="primary" @click="openTrace(row.correlationId)">查看</el-button>
                <el-button v-if="row.status === 'FAILED'" link type="warning" @click="retryLog(row.id)">重试</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination">
            <el-pagination v-model:current-page="page" :total="total" layout="total, prev, pager, next" @current-change="load" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-drawer v-model="traceVisible" title="Correlation 追踪" size="50%">
      <div v-if="trace">
        <p><strong>ID:</strong> {{ trace.correlationId }}</p>
        <h4>同步记录 ({{ trace.syncLogs.length }})</h4>
        <el-table :data="trace.syncLogs" size="small" stripe>
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column prop="message" label="消息" />
        </el-table>
        <h4>平台事件 ({{ trace.events.length }})</h4>
        <el-table :data="trace.events" size="small" stripe>
          <el-table-column prop="eventType" label="类型" width="140" />
          <el-table-column prop="topic" label="Topic" />
        </el-table>
        <h4>SLA 告警 ({{ trace.breaches.length }})</h4>
        <el-table :data="trace.breaches" size="small" stripe>
          <el-table-column prop="metric" label="指标" width="120" />
          <el-table-column prop="message" label="消息" />
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listSyncLogs, retrySync, retrySyncByLogId, type SyncLog } from '@/api/sync'
import { traceCorrelation, type TraceResult } from '@/api/sla'
import { fetchFc3dSyncStatus, triggerFc3dSync, type Fc3dSyncStatus } from '@/api/fc3d'

const loading = ref(false)
const retryLoading = ref(false)
const fc3dLoading = ref(false)
const fc3dStatus = ref<Fc3dSyncStatus | null>(null)
const logs = ref<SyncLog[]>([])
const page = ref(1)
const total = ref(0)
const statusFilter = ref('')
const traceVisible = ref(false)
const trace = ref<TraceResult | null>(null)

async function loadFc3dStatus() {
  try {
    fc3dStatus.value = await fetchFc3dSyncStatus()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载福彩3D同步状态失败')
  }
}

async function syncFc3d(full: boolean) {
  fc3dLoading.value = true
  try {
    fc3dStatus.value = await triggerFc3dSync(full)
    ElMessage.success(full ? '福彩3D 全量同步完成' : '福彩3D 最新同步完成')
    await load()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '福彩3D 同步失败')
  } finally {
    fc3dLoading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const result = await listSyncLogs(page.value, 20, statusFilter.value || undefined)
    logs.value = result.records
    total.value = result.total
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function retry() {
  retryLoading.value = true
  try {
    await retrySync()
    ElMessage.success('已触发重试')
    await load()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '重试失败')
  } finally {
    retryLoading.value = false
  }
}

async function retryLog(logId: number) {
  retryLoading.value = true
  try {
    await retrySyncByLogId(logId)
    ElMessage.success('已重试失败任务')
    await load()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '重试失败')
  } finally {
    retryLoading.value = false
  }
}

async function openTrace(correlationId: string) {
  try {
    trace.value = await traceCorrelation(correlationId)
    traceVisible.value = true
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '追踪失败')
  }
}

onMounted(() => {
  loadFc3dStatus()
  load()
})
</script>

<style scoped>
.page { padding: 4px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
.row { margin-bottom: 16px; }
.hint { margin-top: 12px; color: #909399; font-size: 13px; }
h4 { margin: 16px 0 8px; }
</style>
