<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>福彩3D 智能预测</span>
          <el-button type="primary" :loading="loading" @click="runPredict">混合预测</el-button>
        </div>
      </template>

      <el-space wrap>
        <el-button :loading="loadingRules" @click="runRules">规则引擎</el-button>
        <el-button :loading="loadingAi" @click="runAi">AI 模型</el-button>
      </el-space>

      <div v-if="result" class="result">
        <p>期号：{{ result.issue }}</p>
        <p>
          统计首选号码：
          <el-tag type="danger" class="ball">{{ result.digit1 }}</el-tag>
          <el-tag type="danger" class="ball">{{ result.digit2 }}</el-tag>
          <el-tag type="danger" class="ball">{{ result.digit3 }}</el-tag>
        </p>
        <p>和值：{{ result.sumValue }} · 跨度：{{ result.spanValue }} · 奇偶：{{ result.oddEvenPattern }}</p>
        <p>
          模型：{{ result.modelName }}<template v-if="result.modelVersion"> · 版本 {{ result.modelVersion }}</template>
          · 置信度 {{ (result.confidence * 100).toFixed(1) }}% · 来源
          {{ result.source }}
        </p>
      </div>

      <div v-if="result?.candidates?.length" class="candidates">
        <h4>统计候选列表</h4>
        <el-table :data="result.candidates" size="small" stripe>
          <el-table-column prop="number" label="号码" width="100">
            <template #default="{ row }">
              <el-tag :type="row.number === result?.best ? 'danger' : ''">{{ row.number }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="score" label="评分" width="90" />
          <el-table-column label="统计原因">
            <template #default="{ row }">
              <el-tag
                v-for="(reason, idx) in row.reasons"
                :key="idx"
                size="small"
                class="reason-tag"
              >
                {{ reason }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="combinations">
        <div class="combinations-header">
          <h4>
            Top50 候选组合
            <template v-if="combinationResult">（版本 {{ combinationResult.modelVersion }}）</template>
          </h4>
          <el-space>
            <el-button size="small" :loading="loadingCombinations" @click="runCombinations">
              生成 Top50 候选组合
            </el-button>
            <el-button
              size="small"
              :disabled="!combinationResult?.candidates?.length"
              @click="exportCombinationsCsv"
            >
              导出 CSV
            </el-button>
          </el-space>
        </div>

        <el-table
          v-if="combinationResult?.candidates?.length"
          :data="combinationResult.candidates"
          size="small"
          stripe
          max-height="480"
        >
          <el-table-column prop="rank" label="排名" width="70" />
          <el-table-column prop="number" label="号码" width="90">
            <template #default="{ row }">
              <el-tag>{{ row.number }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="score" label="评分" width="80" />
          <el-table-column label="风险等级" width="100">
            <template #default="{ row }">
              <el-tag :type="riskTagType(row.riskLevel)" size="small">{{ row.riskLevel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="统计原因">
            <template #default="{ row }">
              <el-tag
                v-for="(reason, idx) in row.reasons"
                :key="idx"
                size="small"
                class="reason-tag"
              >
                {{ reason }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <el-alert
        title="统计分析与概率模型解释，不构成中奖承诺或购彩建议"
        type="warning"
        :closable="false"
        show-icon
        class="disclaimer"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchFc3dCombinations,
  predictFc3dAi,
  predictFc3dHybrid,
  predictFc3dRules,
  type Fc3dCombinationResponse,
  type Fc3dPredictResult,
} from '@/api/fc3d'

const loading = ref(false)
const loadingRules = ref(false)
const loadingAi = ref(false)
const loadingCombinations = ref(false)
const result = ref<Fc3dPredictResult | null>(null)
const combinationResult = ref<Fc3dCombinationResponse | null>(null)

async function runPredict() {
  loading.value = true
  try {
    result.value = await predictFc3dHybrid()
    ElMessage.success('福彩3D 混合预测完成')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '预测失败')
  } finally {
    loading.value = false
  }
}

async function runRules() {
  loadingRules.value = true
  try {
    result.value = await predictFc3dRules()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '预测失败')
  } finally {
    loadingRules.value = false
  }
}

async function runAi() {
  loadingAi.value = true
  try {
    result.value = await predictFc3dAi()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '预测失败')
  } finally {
    loadingAi.value = false
  }
}

async function runCombinations() {
  loadingCombinations.value = true
  try {
    combinationResult.value = await fetchFc3dCombinations()
    ElMessage.success(`Top${combinationResult.value.totalCandidates} 候选组合生成完成`)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '候选组合生成失败')
  } finally {
    loadingCombinations.value = false
  }
}

function riskTagType(riskLevel: string): '' | 'success' | 'warning' | 'danger' {
  if (riskLevel === 'LOW') return 'success'
  if (riskLevel === 'MEDIUM') return 'warning'
  if (riskLevel === 'HIGH') return 'danger'
  return ''
}

function exportCombinationsCsv() {
  const candidates = combinationResult.value?.candidates
  if (!candidates?.length) {
    return
  }
  const header = ['rank', 'number', 'score', 'riskLevel', 'reasons']
  const lines = [header.join(',')]
  for (const c of candidates) {
    const reasons = c.reasons.join('; ').replace(/"/g, '""')
    lines.push([c.rank, c.number, c.score, c.riskLevel, `"${reasons}"`].join(','))
  }
  const csv = '\ufeff' + lines.join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `fc3d-top${combinationResult.value?.totalCandidates ?? candidates.length}-candidates.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
</script>

<style scoped>
.page { padding: 4px; }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.result {
  margin-top: 20px;
  line-height: 2.2;
}
.ball {
  margin-right: 8px;
  min-width: 32px;
  text-align: center;
  font-size: 16px;
}
.candidates {
  margin-top: 20px;
}
.combinations {
  margin-top: 24px;
}
.combinations-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.combinations-header h4 {
  margin: 0;
}
.reason-tag {
  margin: 2px 4px 2px 0;
}
.disclaimer {
  margin-top: 16px;
}
</style>
