<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>平台事件流 (Kafka)</span>
          <el-space>
            <el-input v-model="filters.correlationId" placeholder="Correlation ID" clearable style="width: 200px" />
            <el-select v-model="filters.eventType" clearable placeholder="事件类型" style="width: 160px">
              <el-option label="sync.completed" value="sync.completed" />
              <el-option label="sync.failed" value="sync.failed" />
              <el-option label="predict.created" value="predict.created" />
            </el-select>
            <el-button type="primary" @click="load">查询</el-button>
          </el-space>
        </div>
      </template>

      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="eventType" label="类型" width="160" />
        <el-table-column prop="topic" label="Topic" width="180" />
        <el-table-column prop="region" label="区域" width="100" />
        <el-table-column prop="correlationId" label="Correlation" show-overflow-tooltip />
        <el-table-column prop="schemaVersion" label="Schema" width="80" />
        <el-table-column label="已发布" width="90">
          <template #default="{ row }">
            <el-tag :type="row.published ? 'success' : 'info'" size="small">
              {{ row.published ? 'Kafka' : 'No' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="180" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button v-if="row.correlationId" link type="primary" @click="openTrace(row.correlationId)">追踪</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination v-model:current-page="page" :total="total" layout="total, prev, pager, next" @current-change="load" />
      </div>
    </el-card>

    <el-drawer v-model="traceVisible" title="Correlation 追踪" size="45%">
      <pre v-if="traceJson" class="trace-json">{{ traceJson }}</pre>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listEvents, type PlatformEvent } from '@/api/events'
import { traceCorrelation } from '@/api/sla'

const loading = ref(false)
const records = ref<PlatformEvent[]>([])
const page = ref(1)
const total = ref(0)
const filters = reactive({ eventType: '', correlationId: '' })
const traceVisible = ref(false)
const traceJson = ref('')

async function load() {
  loading.value = true
  try {
    const result = await listEvents(
      page.value, 20,
      filters.eventType || undefined,
      undefined,
      filters.correlationId || undefined
    )
    records.value = result.records
    total.value = result.total
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function openTrace(correlationId: string) {
  try {
    const trace = await traceCorrelation(correlationId)
    traceJson.value = JSON.stringify(trace, null, 2)
    traceVisible.value = true
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '追踪失败')
  }
}

onMounted(load)
</script>

<style scoped>
.page { padding: 4px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
.trace-json { font-size: 12px; white-space: pre-wrap; word-break: break-all; }
</style>
