<template>
  <div class="page">
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>24h 运维概览</template>
          <el-descriptions v-if="overview" :column="1" border>
            <el-descriptions-item label="SLA 告警">{{ overview.breaches24h }}</el-descriptions-item>
            <el-descriptions-item label="同步失败">{{ overview.failedSyncs24h }}</el-descriptions-item>
            <el-descriptions-item label="Redis 缓存">{{ overview.cacheEnabled ? '已启用' : '关闭' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>跨区域 SLA / 同步</span>
              <el-button @click="load">刷新</el-button>
            </div>
          </template>
          <el-table v-loading="loading" :data="overview?.regions ?? []" stripe>
            <el-table-column prop="region" label="区域" width="120" />
            <el-table-column prop="totalCalls" label="API 调用" width="100" />
            <el-table-column label="成功率" width="100">
              <template #default="{ row }">{{ row.successRate.toFixed(1) }}%</template>
            </el-table-column>
            <el-table-column label="P95(ms)" width="100">
              <template #default="{ row }">{{ row.p95LatencyMs.toFixed(0) }}</template>
            </el-table-column>
            <el-table-column prop="failedSyncs" label="同步失败" width="100" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getOpsOverview, type OpsOverview } from '@/api/ops'

const loading = ref(false)
const overview = ref<OpsOverview | null>(null)

async function load() {
  loading.value = true
  try {
    overview.value = await getOpsOverview(24)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page { padding: 4px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
