<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>当前运行模型</span>
          <el-button :loading="loadingStatus" @click="loadAll">刷新</el-button>
        </div>
      </template>

      <el-alert
        title="模型运营闭环仅管理已有统计模型的版本、评估记录与生效状态 —— 不新增任何选号或号码生成策略"
        type="warning"
        :closable="false"
        show-icon
        class="disclaimer"
      />

      <el-row :gutter="16" class="status-row" v-if="status">
        <el-col :span="6">
          <el-statistic title="生效版本" :value="status.activeModel ?? '—'" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="最近评估日期" :value="status.lastEvaluation ?? '未评估'" />
        </el-col>
        <el-col :span="6">
          <div class="health-block">
            <div class="stat-title">健康度</div>
            <el-tag :type="healthTagType(status.health)" size="large">{{ healthLabel(status.health) }}</el-tag>
          </div>
        </el-col>
        <el-col :span="6">
          <el-statistic title="Top50 命中率" :value="pct(status.metrics?.top50HitRate)" suffix="%" />
        </el-col>
      </el-row>

      <el-descriptions v-if="activeModel" title="生效模型参数" border :column="3" class="params-block">
        <el-descriptions-item label="版本">{{ activeModel.version }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="activeModel.status === 'ACTIVE' ? 'success' : 'info'">{{ activeModel.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ activeModel.createdTime }}</el-descriptions-item>
        <el-descriptions-item v-for="(value, key) in activeModel.parameters" :key="key" :label="key">
          {{ value }}
        </el-descriptions-item>
      </el-descriptions>

      <div class="select-block">
        <el-button type="primary" :loading="loadingSelect" @click="previewSelection">预览自动选型推荐</el-button>
        <span v-if="selection" class="selection-result">
          推荐版本：<strong>{{ selection.selectedModel ?? '暂无可用候选' }}</strong>
          <template v-if="selection.reason?.length">
            （{{ selection.reason.join('；') }}）
          </template>
        </span>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>模型健康检查</span>
          <el-button :loading="loadingHealth" size="small" @click="loadHealth">刷新</el-button>
        </div>
      </template>

      <div v-if="health" class="health-summary">
        <span class="status-light" :class="healthLightClass(health.status)"></span>
        <span class="health-summary-text">
          总体状态：<strong>{{ healthLabelV2(health.status) }}</strong>
          <template v-if="health.model">（生效模型 {{ health.model }}）</template>
        </span>
      </div>

      <el-table v-if="health" :data="health.checks" size="small" stripe class="checks-table">
        <el-table-column prop="name" label="检查项" width="160">
          <template #default="{ row }">{{ checkNameLabel(row.name) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="healthTagTypeV2(row.status)" size="small">{{ healthLabelV2(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="数值" width="100">
          <template #default="{ row }">{{ row.value ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="说明">
          <template #default="{ row }">{{ row.message ?? '—' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loadingHealth && !health" description="暂无健康检查数据" />
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>模型融合状态（Ensemble）</span>
          <el-button :loading="loadingEnsemble" size="small" @click="loadEnsemble">刷新</el-button>
        </div>
      </template>

      <el-alert
        title="多模型融合仅对各模型已生成的候选做加权投票（Weighted Voting）再排序 —— 不会新增或重新生成任何号码"
        type="info"
        :closable="false"
        show-icon
        class="disclaimer"
      />

      <el-row :gutter="16" class="status-row" v-if="ensemble">
        <el-col :span="8">
          <el-statistic title="融合方式" :value="ensemble.fusionMethod" />
        </el-col>
        <el-col :span="8">
          <el-statistic title="参与模型数量" :value="ensemble.modelVersions.length" />
        </el-col>
        <el-col :span="8">
          <el-statistic title="融合 Top50 数量" :value="ensemble.topCandidates.length" />
        </el-col>
      </el-row>

      <el-table v-if="ensemble" :data="ensembleModelRows" size="small" stripe class="checks-table">
        <el-table-column prop="version" label="参与模型版本" />
        <el-table-column label="融合权重" width="160">
          <template #default="{ row }">
            <el-tag :type="row.version === status?.activeModel ? 'success' : 'info'" size="small">
              {{ pct(row.weight) }}%
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-table v-if="ensemble" :data="ensemble.topCandidates.slice(0, 10)" size="small" stripe class="checks-table">
        <el-table-column type="index" label="排名" width="70" />
        <el-table-column prop="number" label="号码" width="100" />
        <el-table-column label="融合得分" width="100">
          <template #default="{ row }">{{ row.ensembleScore }}</template>
        </el-table-column>
        <el-table-column label="投票模型数" width="110">
          <template #default="{ row }">{{ row.voteCount }}</template>
        </el-table-column>
        <el-table-column label="来源模型">
          <template #default="{ row }">{{ row.sourceModels.join('、') }}</template>
        </el-table-column>
        <el-table-column label="融合理由">
          <template #default="{ row }">{{ row.reasons.join('；') }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loadingEnsemble && !ensemble" description="暂无融合结果" />
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>Ensemble 权重优化（Grid Search）</span>
          <el-button type="primary" :loading="loadingOptimize" size="small" @click="runOptimize">运行权重优化</el-button>
        </div>
      </template>

      <el-alert
        title="自动优化仅基于历史 walk-forward 回测搜索融合权重的推荐值 —— 不会自动上线，需人工确认后才会应用"
        type="info"
        :closable="false"
        show-icon
        class="disclaimer"
      />

      <template v-if="optimization">
        <el-row :gutter="16" class="status-row">
          <el-col :span="8">
            <el-statistic title="优化前 Top50 命中率" :value="pct(optimization.before.top50)" suffix="%" />
          </el-col>
          <el-col :span="8">
            <el-statistic title="优化后 Top50 命中率" :value="pct(optimization.after.top50)" suffix="%" />
          </el-col>
          <el-col :span="8">
            <div class="health-block">
              <div class="stat-title">提升幅度</div>
              <el-tag :type="optimization.improvement > 0 ? 'success' : 'info'" size="large">
                {{ optimization.improvement > 0 ? '+' : '' }}{{ pct(optimization.improvement) }}%
              </el-tag>
            </div>
          </el-col>
        </el-row>

        <el-table :data="optimizationRows" size="small" stripe class="checks-table">
          <el-table-column prop="version" label="模型版本" />
          <el-table-column label="当前权重" width="140">
            <template #default="{ row }">{{ pct(row.current) }}%</template>
          </el-table-column>
          <el-table-column label="推荐权重" width="140">
            <template #default="{ row }">
              <el-tag :type="row.recommended !== row.current ? 'success' : 'info'" size="small">
                {{ pct(row.recommended) }}%
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <div class="select-block">
          <el-button
            type="warning"
            :loading="loadingApplyWeights"
            @click="applyRecommendedWeights"
          >
            应用推荐权重
          </el-button>
          <span class="selection-result">评估周期数：{{ optimization.evaluatedPeriods }}（搜索方式：{{ optimization.searchMethod }}）</span>
        </div>
      </template>
      <el-empty v-if="!loadingOptimize && !optimization" description="点击「运行权重优化」以基于历史回测搜索推荐权重" />
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>Ensemble Report（融合性能报告）</span>
          <el-button :loading="loadingReport" size="small" @click="loadReport">刷新</el-button>
        </div>
      </template>

      <el-alert
        title="只读性能报告 —— 基于 walk-forward 回测汇总统计，不生成号码，不影响融合权重"
        type="info"
        :closable="false"
        show-icon
        class="disclaimer"
      />

      <template v-if="report">
        <div class="health-summary">
          <span class="status-light" :class="healthLightClass(report.health)"></span>
          <span class="health-summary-text">
            模型：<strong>{{ report.ensembleLabel }}</strong>　
            评估周期：最近 {{ report.evaluatedPeriods }} 期　
            总体健康：<strong>{{ healthLabelV2(report.health) }}</strong>
          </span>
        </div>

        <div class="report-section-title">覆盖能力</div>
        <el-row :gutter="16" class="status-row">
          <el-col :span="8">
            <el-statistic title="Top10" :value="pct(report.coverage.top10)" suffix="%" />
          </el-col>
          <el-col :span="8">
            <el-statistic title="Top20" :value="pct(report.coverage.top20)" suffix="%" />
          </el-col>
          <el-col :span="8">
            <el-statistic title="Top50" :value="pct(report.coverage.top50)" suffix="%" />
          </el-col>
        </el-row>

        <div class="report-section-title">稳定性</div>
        <el-row :gutter="16" class="status-row">
          <el-col :span="8">
            <el-statistic title="平均 Top50" :value="pct(report.stability.average)" suffix="%" />
          </el-col>
          <el-col :span="8">
            <el-statistic title="波动（标准差）" :value="pct(report.stability.volatility)" suffix="%" />
          </el-col>
          <el-col :span="8">
            <el-statistic title="最大回撤" :value="pct(report.stability.maxDrawdown)" suffix="%" />
          </el-col>
        </el-row>

        <div class="report-section-title">时间窗口（按季度）</div>
        <el-table :data="report.timeWindows" size="small" stripe class="checks-table">
          <el-table-column prop="label" label="窗口" width="140" />
          <el-table-column label="Top50 命中率" width="140">
            <template #default="{ row }">{{ pct(row.top50HitRate) }}%</template>
          </el-table-column>
          <el-table-column label="评估期数">
            <template #default="{ row }">{{ row.evaluatedPeriods }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!report.timeWindows.length" description="暂无时间窗口数据（历史数据不足或缺失开奖日期）" />

        <div class="report-section-title">健康检查</div>
        <el-table :data="report.healthChecks" size="small" stripe class="checks-table">
          <el-table-column prop="name" label="检查项" width="160">
            <template #default="{ row }">{{ checkNameLabel(row.name) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="healthTagTypeV2(row.status)" size="small">{{ healthLabelV2(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="说明">
            <template #default="{ row }">{{ row.message ?? '—' }}</template>
          </el-table-column>
        </el-table>
      </template>
      <el-empty v-if="!loadingReport && !report" description="暂无融合性能报告" />
    </el-card>

    <el-card shadow="never" v-if="rollback?.rollbackSuggested" class="rollback-card">
      <template #header>
        <div class="card-header">
          <span>⚠️ 回滚建议（仅供参考，需人工确认）</span>
        </div>
      </template>
      <p>
        当前生效模型 <strong>{{ rollback.current }}</strong> 的 Top50 命中率出现连续下降，
        建议回退至 <strong>{{ rollback.fallback ?? '（未找到可用历史版本）' }}</strong>。
      </p>
      <ul class="rollback-reasons">
        <li v-for="(r, idx) in rollback.reason" :key="idx">{{ r }}</li>
      </ul>
      <el-button
        v-if="rollback.fallback"
        type="warning"
        :loading="loadingRollbackApply"
        @click="applyRollback"
      >
        回退到 {{ rollback.fallback }}
      </el-button>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>历史模型排行榜</span>
        </div>
      </template>
      <el-table :data="leaderboard" size="small" stripe v-loading="loadingLeaderboard">
        <el-table-column prop="rank" label="排名" width="70" />
        <el-table-column prop="version" label="版本" width="180" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
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
        <el-table-column label="较随机基准提升">
          <template #default="{ row }">{{ pct(row.improvementVsRandom) }}%</template>
        </el-table-column>
        <el-table-column label="较纯频率提升">
          <template #default="{ row }">{{ pct(row.improvementVsFrequency) }}%</template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button
              size="small"
              link
              type="primary"
              :disabled="row.status !== 'ACTIVE' || row.version === status?.activeModel"
              @click="promote(row.version)"
            >
              设为生效
            </el-button>
            <el-button
              size="small"
              link
              type="danger"
              :disabled="row.status !== 'ACTIVE'"
              @click="deactivate(row.version)"
            >
              停用
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!leaderboard.length" description="暂无历史评估记录 —— 请先在「模型评估与参数优化」页面运行对比或实验" />
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>模型切换记录</span>
        </div>
      </template>
      <el-table :data="switchLog" size="small" stripe v-loading="loadingSwitchLog">
        <el-table-column label="切换时间" width="200">
          <template #default="{ row }">{{ row.switchedAt }}</template>
        </el-table-column>
        <el-table-column label="原版本" width="140">
          <template #default="{ row }">{{ row.fromVersion ?? '（初始）' }}</template>
        </el-table-column>
        <el-table-column label="新版本" width="140">
          <template #default="{ row }">{{ row.toVersion ?? '（已停用）' }}</template>
        </el-table-column>
        <el-table-column label="操作人" width="120">
          <template #default="{ row }">{{ row.operator ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="原因">
          <template #default="{ row }">{{ row.reason ?? '—' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!switchLog.length" description="暂无模型切换记录" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  activateFc3dModel,
  applyFc3dEnsembleWeights,
  deactivateFc3dModel,
  fetchFc3dEnsemble,
  fetchFc3dEnsembleReport,
  fetchFc3dModelHealth,
  fetchFc3dModelLeaderboard,
  fetchFc3dModelRegistry,
  fetchFc3dModelRollback,
  fetchFc3dModelSelection,
  fetchFc3dModelStatus,
  fetchFc3dModelSwitchLog,
  optimizeFc3dEnsembleWeights,
  type Fc3dEnsembleOptimizationResponse,
  type Fc3dEnsembleReportResponse,
  type Fc3dEnsembleResponse,
  type Fc3dModelHealthResponse,
  type Fc3dModelInfo,
  type Fc3dModelLeaderboardEntry,
  type Fc3dModelRollbackSuggestion,
  type Fc3dModelSelectionResult,
  type Fc3dModelStatusResponse,
  type Fc3dModelSwitchRecord,
} from '@/api/fc3d'

const status = ref<Fc3dModelStatusResponse | null>(null)
const registry = ref<Fc3dModelInfo[]>([])
const leaderboard = ref<Fc3dModelLeaderboardEntry[]>([])
const switchLog = ref<Fc3dModelSwitchRecord[]>([])
const selection = ref<Fc3dModelSelectionResult | null>(null)
const health = ref<Fc3dModelHealthResponse | null>(null)
const rollback = ref<Fc3dModelRollbackSuggestion | null>(null)
const ensemble = ref<Fc3dEnsembleResponse | null>(null)
const optimization = ref<Fc3dEnsembleOptimizationResponse | null>(null)
const report = ref<Fc3dEnsembleReportResponse | null>(null)

const loadingStatus = ref(false)
const loadingLeaderboard = ref(false)
const loadingSwitchLog = ref(false)
const loadingSelect = ref(false)
const loadingHealth = ref(false)
const loadingRollbackApply = ref(false)
const loadingEnsemble = ref(false)
const loadingOptimize = ref(false)
const loadingApplyWeights = ref(false)
const loadingReport = ref(false)

const activeModel = computed(() => registry.value.find((m) => m.production) ?? null)
const ensembleModelRows = computed(() => {
  if (!ensemble.value) return []
  return ensemble.value.modelVersions.map((version) => ({
    version,
    weight: ensemble.value?.modelWeights?.[version] ?? 0,
  }))
})
const optimizationRows = computed(() => {
  if (!optimization.value) return []
  return optimization.value.modelVersions.map((version) => ({
    version,
    current: optimization.value?.currentWeights?.[version] ?? 0,
    recommended: optimization.value?.bestWeights?.[version] ?? 0,
  }))
})

function pct(rate?: number | null) {
  if (rate === undefined || rate === null) return '—'
  return Math.round(rate * 10000) / 100
}

function healthLabel(health?: string) {
  const map: Record<string, string> = { GOOD: '良好', WARN: '预警', POOR: '较差', UNKNOWN: '暂无数据' }
  return map[health ?? ''] ?? health ?? '暂无数据'
}

function healthTagType(health?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (health === 'GOOD') return 'success'
  if (health === 'WARN') return 'warning'
  if (health === 'POOR') return 'danger'
  return 'info'
}

// Sprint 10-E: comprehensive health check (GOOD/WARNING/FAILED) — distinct from the
// simpler GOOD/WARN/POOR indicator returned by /model/status above.
function healthLabelV2(value?: string) {
  const map: Record<string, string> = { GOOD: '良好', WARNING: '预警', FAILED: '异常' }
  return map[value ?? ''] ?? value ?? '暂无数据'
}

function healthTagTypeV2(value?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (value === 'GOOD') return 'success'
  if (value === 'WARNING') return 'warning'
  if (value === 'FAILED') return 'danger'
  return 'info'
}

function healthLightClass(value?: string) {
  if (value === 'GOOD') return 'light-good'
  if (value === 'WARNING') return 'light-warning'
  if (value === 'FAILED') return 'light-failed'
  return 'light-unknown'
}

function checkNameLabel(name: string) {
  const map: Record<string, string> = {
    production: '生产模型存在',
    lastEvaluation: '最近评估时间',
    top50: 'Top50 命中率',
    modelFreshness: '模型更新时效',
    coverage: 'Top50 覆盖率',
    volatility: '波动性',
    maxDrawdown: '最大回撤',
    data: '数据可用性',
  }
  return map[name] ?? name
}

async function loadStatus() {
  loadingStatus.value = true
  try {
    ;[status.value, registry.value] = await Promise.all([fetchFc3dModelStatus(), fetchFc3dModelRegistry()])
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载模型状态失败')
  } finally {
    loadingStatus.value = false
  }
}

async function loadLeaderboard() {
  loadingLeaderboard.value = true
  try {
    leaderboard.value = await fetchFc3dModelLeaderboard()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载模型排行榜失败')
  } finally {
    loadingLeaderboard.value = false
  }
}

async function loadSwitchLog() {
  loadingSwitchLog.value = true
  try {
    switchLog.value = await fetchFc3dModelSwitchLog()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载模型切换记录失败')
  } finally {
    loadingSwitchLog.value = false
  }
}

async function loadHealth() {
  loadingHealth.value = true
  try {
    ;[health.value, rollback.value] = await Promise.all([fetchFc3dModelHealth(), fetchFc3dModelRollback()])
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载模型健康检查失败')
  } finally {
    loadingHealth.value = false
  }
}

async function loadEnsemble() {
  loadingEnsemble.value = true
  try {
    ensemble.value = await fetchFc3dEnsemble()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载模型融合状态失败')
  } finally {
    loadingEnsemble.value = false
  }
}

async function runOptimize() {
  loadingOptimize.value = true
  try {
    optimization.value = await optimizeFc3dEnsembleWeights()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '权重优化失败')
  } finally {
    loadingOptimize.value = false
  }
}

async function applyRecommendedWeights() {
  if (!optimization.value) return
  const { modelVersions, bestWeights } = optimization.value
  try {
    await ElMessageBox.confirm(
      '确认将推荐权重应用到当前 Ensemble 融合吗？此操作会立即影响后续的融合预测与回测。',
      '应用推荐权重确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  loadingApplyWeights.value = true
  try {
    await applyFc3dEnsembleWeights(modelVersions, bestWeights)
    ElMessage.success('已应用推荐权重')
    await Promise.all([loadEnsemble(), runOptimize()])
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '应用推荐权重失败')
  } finally {
    loadingApplyWeights.value = false
  }
}

async function loadReport() {
  loadingReport.value = true
  try {
    report.value = await fetchFc3dEnsembleReport()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载融合性能报告失败')
  } finally {
    loadingReport.value = false
  }
}

async function applyRollback() {
  if (!rollback.value?.fallback) return
  const fallback = rollback.value.fallback
  try {
    await ElMessageBox.confirm(
      `确认将生效模型回退至 ${fallback} 吗？此操作会立即切换 Predict 使用的模型版本。`,
      '回滚确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  loadingRollbackApply.value = true
  try {
    await activateFc3dModel(fallback, 'Top50连续下降，人工确认回滚')
    ElMessage.success(`已回退至 ${fallback}`)
    await loadAll()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '回滚失败')
  } finally {
    loadingRollbackApply.value = false
  }
}

async function previewSelection() {
  loadingSelect.value = true
  try {
    selection.value = await fetchFc3dModelSelection(20)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '自动选型推荐失败')
  } finally {
    loadingSelect.value = false
  }
}

async function promote(version: string) {
  try {
    await activateFc3dModel(version)
    ElMessage.success(`已将 ${version} 设为生效模型`)
    await loadAll()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '切换生效模型失败')
  }
}

async function deactivate(version: string) {
  try {
    await ElMessageBox.confirm(`确认停用模型 ${version} 吗？停用后该版本不会再参与自动选型或预测。`, '停用确认', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deactivateFc3dModel(version, '人工手动停用')
    ElMessage.success(`已停用 ${version}`)
    await loadAll()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '停用模型失败')
  }
}

async function loadAll() {
  await Promise.all([loadStatus(), loadLeaderboard(), loadSwitchLog(), loadHealth(), loadEnsemble(), loadReport()])
}

onMounted(loadAll)
</script>

<style scoped>
.page { padding: 4px; display: flex; flex-direction: column; gap: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.disclaimer { margin-bottom: 16px; }
.status-row { margin-bottom: 16px; }
.health-block { text-align: center; }
.stat-title { font-size: 13px; color: #909399; margin-bottom: 8px; }
.params-block { margin-top: 8px; }
.select-block { margin-top: 16px; display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.selection-result { color: #606266; }
.health-summary { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.health-summary-text { color: #303133; font-size: 14px; }
.status-light { display: inline-block; width: 14px; height: 14px; border-radius: 50%; flex-shrink: 0; }
.light-good { background-color: #67c23a; box-shadow: 0 0 6px #67c23a; }
.light-warning { background-color: #e6a23c; box-shadow: 0 0 6px #e6a23c; }
.light-failed { background-color: #f56c6c; box-shadow: 0 0 6px #f56c6c; }
.light-unknown { background-color: #c0c4cc; }
.checks-table { margin-top: 8px; }
.report-section-title { font-size: 13px; font-weight: 600; color: #606266; margin: 16px 0 8px; }
.rollback-card { border-color: #e6a23c; }
.rollback-reasons { margin: 8px 0 12px 20px; color: #606266; font-size: 13px; }
</style>
