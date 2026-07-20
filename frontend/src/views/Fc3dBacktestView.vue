<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>福彩3D 模型回测评估</span>
          <el-button type="primary" :loading="loading" @click="run">运行回测评估</el-button>
        </div>
      </template>

      <el-alert
        title="统计分析与概率模型评估，不构成中奖承诺或购彩建议；本页仅评估既有统计模型，不生成新的预测号码"
        type="warning"
        :closable="false"
        show-icon
        class="disclaimer"
      />

      <el-form inline class="form">
        <el-form-item label="最小训练期数">
          <el-input-number v-model="minHistory" :min="10" :max="500" />
        </el-form-item>
        <el-form-item label="评估期数（近 N 期，walk-forward）">
          <el-input-number v-model="evalPeriods" :min="10" :max="2000" :step="50" />
        </el-form-item>
        <el-form-item label="候选池 TopN">
          <el-input-number v-model="topN" :min="1" :max="20" />
        </el-form-item>
      </el-form>

      <div v-if="result">
        <el-row :gutter="16" class="metrics">
          <el-col :span="8">
            <el-statistic title="Top1 命中率" :value="pct(result.top1HitRate)" suffix="%" />
          </el-col>
          <el-col :span="8">
            <el-statistic title="Top3 命中率" :value="pct(result.top3HitRate)" suffix="%" />
          </el-col>
          <el-col :span="8">
            <el-statistic title="Top5 命中率" :value="pct(result.top5HitRate)" suffix="%" />
          </el-col>
        </el-row>
        <el-row :gutter="16" class="metrics">
          <el-col :span="8">
            <el-statistic title="和值准确率" :value="pct(result.sumAccuracy)" suffix="%" />
          </el-col>
          <el-col :span="8">
            <el-statistic title="奇偶准确率" :value="pct(result.oddEvenAccuracy)" suffix="%" />
          </el-col>
          <el-col :span="8">
            <el-statistic title="评估期数" :value="result.totalPeriods" />
          </el-col>
        </el-row>

        <h4>位置准确率（Top1 候选）</h4>
        <el-table :data="positionRows" size="small" stripe>
          <el-table-column prop="label" label="位置" width="120" />
          <el-table-column label="准确率">
            <template #default="{ row }">{{ pct(row.value) }}%</template>
          </el-table-column>
        </el-table>

        <el-descriptions :column="2" border class="result">
          <el-descriptions-item label="彩种">{{ result.lotteryType }}</el-descriptions-item>
          <el-descriptions-item label="最小训练期数">{{ result.minHistory }}</el-descriptions-item>
          <el-descriptions-item label="候选池 TopN">{{ result.topN }}</el-descriptions-item>
          <el-descriptions-item label="说明" :span="2">{{ result.note }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <el-empty v-else description="点击「运行回测评估」查看模型评估指标" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { runFc3dBacktestEvaluate, type Fc3dBacktestResult } from '@/api/fc3d'

const loading = ref(false)
const result = ref<Fc3dBacktestResult | null>(null)
const minHistory = ref(30)
const evalPeriods = ref(200)
const topN = ref(5)

const POSITION_LABELS: Record<string, string> = {
  hundreds: '百位',
  tens: '十位',
  units: '个位',
}

const positionRows = computed(() => {
  if (!result.value) return []
  return Object.entries(result.value.positionAccuracy).map(([key, value]) => ({
    label: POSITION_LABELS[key] ?? key,
    value,
  }))
})

function pct(rate: number) {
  return Math.round(rate * 10000) / 100
}

async function run() {
  loading.value = true
  try {
    result.value = await runFc3dBacktestEvaluate(minHistory.value, evalPeriods.value, topN.value)
    ElMessage.success('福彩3D 回测评估完成')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '回测评估失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page { padding: 4px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.disclaimer { margin-bottom: 16px; }
.form { margin-bottom: 8px; }
.metrics { margin-bottom: 16px; }
.result { margin-top: 16px; }
</style>
