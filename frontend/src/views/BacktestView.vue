<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>回测分析</span>
          <el-button type="primary" :loading="loading" @click="run">运行回测</el-button>
        </div>
      </template>

      <el-form inline>
        <el-form-item label="名称">
          <el-input v-model="form.name" style="width: 160px" />
        </el-form-item>
        <el-form-item label="最小历史期数">
          <el-input-number v-model="form.minHistory" :min="10" :max="100" />
        </el-form-item>
        <el-form-item label="Top 红球">
          <el-input-number v-model="form.topRed" :min="6" :max="20" />
        </el-form-item>
      </el-form>

      <el-descriptions v-if="result" :column="2" border class="result">
        <el-descriptions-item label="回测期数">{{ result.totalPeriods }}</el-descriptions-item>
        <el-descriptions-item label="红球命中率">{{ (result.redHitRate * 100).toFixed(2) }}%</el-descriptions-item>
        <el-descriptions-item label="平均命中红球">{{ result.avgRedHits.toFixed(2) }} / 6</el-descriptions-item>
        <el-descriptions-item label="蓝球命中率">{{ (result.blueHitRate * 100).toFixed(2) }}%</el-descriptions-item>
        <el-descriptions-item label="最大单期红球命中">{{ result.maxRedHits }}</el-descriptions-item>
        <el-descriptions-item label="报告 ID">{{ result.reportId }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { runBacktest, type BacktestResult } from '@/api/backtest'

const loading = ref(false)
const result = ref<BacktestResult | null>(null)
const form = reactive({
  name: 'sprint1-default',
  minHistory: 30,
  topRed: 12
})

async function run() {
  loading.value = true
  try {
    result.value = await runBacktest(form)
    ElMessage.success('回测完成')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '回测失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page {
  padding: 4px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.result {
  margin-top: 20px;
}
</style>
