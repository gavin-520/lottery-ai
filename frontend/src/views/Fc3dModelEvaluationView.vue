<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>福彩3D 模型评估与参数优化</span>
        </div>
      </template>

      <el-alert
        title="统计模型科学评估，不生成也不推荐任何号码；随机基准仅用于内部对照，从不作为推荐结果展示"
        type="warning"
        :closable="false"
        show-icon
        class="disclaimer"
      />

      <el-form inline class="form">
        <el-form-item label="最小训练期数">
          <el-input-number v-model="minHistory" :min="10" :max="500" />
        </el-form-item>
        <el-form-item label="评估期数（walk-forward）">
          <el-input-number v-model="evalPeriods" :min="10" :max="2000" :step="50" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loadingCompare" @click="runCompare">
            运行模型对比评估
          </el-button>
        </el-form-item>
      </el-form>

      <div v-if="currentModel" class="metrics-block">
        <h4>当前模型指标（{{ currentModel.modelName }}，评估 {{ currentModel.evaluatedPeriods }} 期）</h4>
        <el-row :gutter="16" class="metrics">
          <el-col :span="8">
            <el-statistic title="Top10 命中率" :value="pct(currentModel.top10HitRate)" suffix="%" />
          </el-col>
          <el-col :span="8">
            <el-statistic title="Top20 命中率" :value="pct(currentModel.top20HitRate)" suffix="%" />
          </el-col>
          <el-col :span="8">
            <el-statistic title="Top50 命中率" :value="pct(currentModel.top50HitRate)" suffix="%" />
          </el-col>
        </el-row>

        <h4>与基准模型比较（提升百分比，基于 Top50 命中率）</h4>
        <el-row :gutter="16" class="metrics">
          <el-col :span="12">
            <el-statistic title="较随机基准提升" :value="pct(currentModel.improvementVsRandom)" suffix="%" />
          </el-col>
          <el-col :span="12">
            <el-statistic title="较纯频率模型提升" :value="pct(currentModel.improvementVsFrequency)" suffix="%" />
          </el-col>
        </el-row>

        <el-table :data="comparisonResults" size="small" stripe class="comparison-table">
          <el-table-column prop="modelName" label="模型" width="200">
            <template #default="{ row }">{{ modelLabel(row.modelName) }}</template>
          </el-table-column>
          <el-table-column label="Top10">
            <template #default="{ row }">{{ pct(row.top10HitRate) }}%</template>
          </el-table-column>
          <el-table-column label="Top20">
            <template #default="{ row }">{{ pct(row.top20HitRate) }}%</template>
          </el-table-column>
          <el-table-column label="Top50">
            <template #default="{ row }">{{ pct(row.top50HitRate) }}%</template>
          </el-table-column>
          <el-table-column label="较随机基准">
            <template #default="{ row }">{{ pct(row.improvementVsRandom) }}%</template>
          </el-table-column>
          <el-table-column label="较纯频率模型">
            <template #default="{ row }">{{ pct(row.improvementVsFrequency) }}%</template>
          </el-table-column>
        </el-table>
      </div>
      <el-empty v-else description="点击「运行模型对比评估」查看当前模型 / 随机基准 / 纯频率模型的对比指标" />
    </el-card>

    <el-card shadow="never" class="experiments-card">
      <template #header>
        <div class="card-header">
          <span>参数优化实验</span>
          <el-button type="primary" :loading="loadingExperiments" @click="runExperiments">
            运行批量实验
          </el-button>
        </div>
      </template>

      <el-alert
        title="批量参数实验默认在服务端关闭（lottery.fc3d.model.experiment.enabled=false），需管理员显式开启后才可运行"
        type="info"
        :closable="false"
        show-icon
        class="disclaimer"
      />

      <div class="param-sets">
        <div v-for="(row, idx) in parameterSets" :key="idx" class="param-row">
          <span class="param-label">weightFrequency</span>
          <el-input-number v-model="row.frequency" :min="0" :max="1" :step="0.05" size="small" />
          <span class="param-label">weightMissing</span>
          <el-input-number v-model="row.missing" :min="0" :max="1" :step="0.05" size="small" />
          <span class="param-label">weightSum</span>
          <el-input-number v-model="row.sum" :min="0" :max="1" :step="0.05" size="small" />
          <span class="param-label">weightOddEven</span>
          <el-input-number v-model="row.oddEven" :min="0" :max="1" :step="0.05" size="small" />
          <span class="param-label">weightSpan</span>
          <el-input-number v-model="row.span" :min="0" :max="1" :step="0.05" size="small" />
          <el-button size="small" type="danger" link @click="removeParamSet(idx)">删除</el-button>
        </div>
        <el-button size="small" @click="addParamSet">+ 添加参数组合</el-button>
      </div>

      <h4>参数实验排行榜（按 Top50 命中率排序）</h4>
      <el-table :data="leaderboard" size="small" stripe>
        <el-table-column prop="experimentId" label="实验编号" width="140" />
        <el-table-column label="权重参数">
          <template #default="{ row }">
            <el-tag v-for="(value, key) in row.parameters" :key="key" size="small" class="reason-tag">
              {{ key }}={{ value }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Top10" width="90">
          <template #default="{ row }">{{ pct(row.metrics.top10HitRate) }}%</template>
        </el-table-column>
        <el-table-column label="Top20" width="90">
          <template #default="{ row }">{{ pct(row.metrics.top20HitRate) }}%</template>
        </el-table-column>
        <el-table-column label="Top50" width="90">
          <template #default="{ row }">{{ pct(row.metrics.top50HitRate) }}%</template>
        </el-table-column>
        <el-table-column prop="modelVersion" label="版本" width="160" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  compareFc3dModels,
  runFc3dModelExperiments,
  type Fc3dExperimentResult,
  type Fc3dModelEvaluationResult,
  type Fc3dWeightOverride,
} from '@/api/fc3d'

const minHistory = ref(30)
const evalPeriods = ref(200)
const loadingCompare = ref(false)
const loadingExperiments = ref(false)

const comparisonResults = ref<Fc3dModelEvaluationResult[]>([])
const experimentResults = ref<Fc3dExperimentResult[]>([])
const parameterSets = ref<Fc3dWeightOverride[]>([
  { frequency: 0.35, missing: 0.2, sum: 0.25, oddEven: 0.15, span: 0.1 },
])

const MODEL_LABELS: Record<string, string> = {
  'fc3d-combination-model': '当前统计模型',
  'random-baseline': '随机基准',
  'frequency-only-baseline': '纯频率模型',
}

function modelLabel(modelName: string) {
  return MODEL_LABELS[modelName] ?? modelName
}

const currentModel = computed(() =>
  comparisonResults.value.find((m) => m.modelName === 'fc3d-combination-model') ?? null
)

const leaderboard = computed(() =>
  [...experimentResults.value].sort((a, b) => b.metrics.top50HitRate - a.metrics.top50HitRate)
)

function pct(rate: number) {
  return Math.round(rate * 10000) / 100
}

function addParamSet() {
  parameterSets.value.push({ frequency: 0.35, missing: 0.2, sum: 0.25, oddEven: 0.15, span: 0.1 })
}

function removeParamSet(idx: number) {
  parameterSets.value.splice(idx, 1)
}

async function runCompare() {
  loadingCompare.value = true
  try {
    comparisonResults.value = await compareFc3dModels(minHistory.value, evalPeriods.value)
    ElMessage.success('模型对比评估完成')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '模型对比评估失败')
  } finally {
    loadingCompare.value = false
  }
}

async function runExperiments() {
  if (!parameterSets.value.length) {
    ElMessage.warning('请先添加至少一组参数组合')
    return
  }
  loadingExperiments.value = true
  try {
    const results = await runFc3dModelExperiments(parameterSets.value, minHistory.value, evalPeriods.value)
    experimentResults.value = [...experimentResults.value, ...results]
    ElMessage.success(`完成 ${results.length} 组参数实验`)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '参数实验运行失败（可能服务端未开启该功能）')
  } finally {
    loadingExperiments.value = false
  }
}
</script>

<style scoped>
.page { padding: 4px; display: flex; flex-direction: column; gap: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.disclaimer { margin-bottom: 16px; }
.form { margin-bottom: 8px; }
.metrics-block h4 { margin-top: 20px; }
.metrics { margin-bottom: 16px; }
.comparison-table { margin-top: 12px; }
.experiments-card { margin-top: 0; }
.param-sets { margin-bottom: 16px; }
.param-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.param-label { font-size: 12px; color: #606266; }
.reason-tag { margin: 2px 4px 2px 0; }
</style>
