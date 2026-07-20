<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>福彩3D Top50 Walk-forward 回测明细</span>
          <el-button type="primary" :loading="loading" @click="run">运行明细回测</el-button>
        </div>
      </template>

      <el-alert
        title="仅验证既有组合模型的历史表现 —— 每期用 history[0:i] 生成 TopN，对照第 i 期实际开奖；不生成新号码，不构成购彩建议"
        type="warning"
        :closable="false"
        show-icon
        class="disclaimer"
      />

      <el-form inline class="form">
        <el-form-item label="最小训练期数">
          <el-input-number v-model="minHistory" :min="10" :max="500" />
        </el-form-item>
        <el-form-item label="评估期数（近 N 期）">
          <el-input-number v-model="evalPeriods" :min="10" :max="2000" :step="50" />
        </el-form-item>
        <el-form-item label="TopN">
          <el-input-number v-model="topN" :min="1" :max="100" :step="10" />
        </el-form-item>
        <el-form-item label="模型版本">
          <el-input v-model="modelVersion" clearable placeholder="默认：当前生产模型" style="width: 180px" />
        </el-form-item>
      </el-form>

      <template v-if="result">
        <el-row :gutter="16" class="metrics">
          <el-col :span="4">
            <el-statistic title="模型版本" :value="result.modelVersion" />
          </el-col>
          <el-col :span="4">
            <el-statistic title="评估期数" :value="result.evaluatedPeriods" />
          </el-col>
          <el-col :span="4">
            <el-statistic title="TopN 命中率" :value="pct(result.hitRate)" suffix="%" />
          </el-col>
          <el-col :span="4">
            <el-statistic title="命中次数" :value="result.hitCount" />
          </el-col>
          <el-col :span="4">
            <el-statistic title="平均命中排名" :value="result.averageHitRank" :precision="2" />
          </el-col>
          <el-col :span="4">
            <el-statistic title="最长连续不中" :value="result.longestMissStreak" />
          </el-col>
        </el-row>

        <el-table :data="result.details" size="small" stripe row-key="issue" v-loading="loading">
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="expand-block">
                <div class="expand-title">预测 Top{{ result.topN }}（训练期数 {{ row.trainPeriods }}）</div>
                <div class="candidate-grid">
                  <el-tag
                    v-for="c in row.predictedTop50"
                    :key="c.number"
                    :type="c.number === row.actualNumber ? 'success' : 'info'"
                    size="small"
                    class="candidate-tag"
                  >
                    #{{ c.rank }} {{ c.number }}（{{ c.score }}）
                  </el-tag>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="issue" label="期号" width="120" />
          <el-table-column prop="actualNumber" label="开奖号码" width="100" />
          <el-table-column label="是否命中" width="100">
            <template #default="{ row }">
              <el-tag :type="row.hit ? 'success' : 'info'" size="small">
                {{ row.hit ? '✅ 命中' : '未中' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Top 排名" width="110">
            <template #default="{ row }">
              {{ row.hit ? `Rank ${row.hitRank}` : '—' }}
            </template>
          </el-table-column>
          <el-table-column label="命中层级" width="100">
            <template #default="{ row }">
              <el-tag :type="hitLevelType(row.hitLevel)" size="small">{{ row.hitLevel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="命中得分" width="100">
            <template #default="{ row }">{{ row.hitScore ?? '—' }}</template>
          </el-table-column>
          <el-table-column prop="modelVersion" label="模型版本" width="140" />
          <el-table-column label="预测 TopN 摘要">
            <template #default="{ row }">
              {{ topPreview(row.predictedTop50) }}
            </template>
          </el-table-column>
        </el-table>
      </template>
      <el-empty v-else description="点击「运行明细回测」查看逐期 TopN 与命中情况" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchFc3dBacktestDetails, type Fc3dBacktestCandidate, type Fc3dBacktestDetailResponse } from '@/api/fc3d'

const loading = ref(false)
const result = ref<Fc3dBacktestDetailResponse | null>(null)
const minHistory = ref(30)
const evalPeriods = ref(200)
const topN = ref(50)
const modelVersion = ref('')

function pct(rate: number) {
  return Math.round(rate * 10000) / 100
}

function topPreview(candidates: Fc3dBacktestCandidate[]) {
  return candidates.slice(0, 5).map((c) => c.number).join('、') + '…'
}

function hitLevelType(level: string): 'success' | 'warning' | 'info' | 'danger' {
  if (level === 'TOP1' || level === 'TOP10') return 'success'
  if (level === 'TOP20') return 'warning'
  if (level === 'MISS') return 'info'
  return 'info'
}

async function run() {
  loading.value = true
  try {
    result.value = await fetchFc3dBacktestDetails(
      minHistory.value,
      evalPeriods.value,
      topN.value,
      modelVersion.value || undefined
    )
    ElMessage.success(`明细回测完成：评估 ${result.value.evaluatedPeriods} 期，命中率 ${pct(result.value.hitRate)}%`)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '明细回测失败')
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
.expand-block { padding: 8px 16px 16px; }
.expand-title { font-size: 13px; color: #606266; margin-bottom: 8px; }
.candidate-grid { display: flex; flex-wrap: wrap; gap: 6px; }
.candidate-tag { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
</style>
