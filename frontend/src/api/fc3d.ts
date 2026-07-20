import http, { type ApiResult } from './http'

export interface Fc3dDraw {
  id: number
  issue: string
  digit1: number
  digit2: number
  digit3: number
  sumValue: number
  spanValue: number
  oddEvenPattern: string
  drawDate?: string
  lotteryType?: string
}

export interface Fc3dCandidate {
  number: string
  score: number
  reasons: string[]
}

export interface Fc3dPredictResult {
  lotteryType: string
  issue: string
  digit1: number
  digit2: number
  digit3: number
  sumValue: number
  spanValue: number
  oddEvenPattern: string
  modelName: string
  /** Statistical model version (Sprint 10-A), e.g. "v2". Optional for backward compatibility. */
  modelVersion?: string
  confidence: number
  source: string
  /** Ranked statistical candidates (explainable). Optional for backward compatibility. */
  candidates?: Fc3dCandidate[]
  best?: string
}

export interface BallFrequencyItem {
  ball: number
  count: number
  type: string
}

export interface Fc3dAnalyticsSummary {
  lotteryType: string
  totalPeriods: number
  digitFrequency: BallFrequencyItem[]
  pos1Frequency: BallFrequencyItem[]
  pos2Frequency: BallFrequencyItem[]
  pos3Frequency: BallFrequencyItem[]
  sumDistribution: Record<string, number>
  spanDistribution: Record<string, number>
  oddEvenDistribution: Record<string, number>
}

/** Richer frequency response (Sprint 9-A) — superset of Fc3dAnalyticsSummary. */
export interface Fc3dFrequencyResponse extends Fc3dAnalyticsSummary {
  hundreds: Record<string, number>
  tens: Record<string, number>
  units: Record<string, number>
}

export interface Fc3dMissingItem {
  position: string
  number: number
  missing: number
}

export interface Fc3dMissingResponse {
  lotteryType: string
  totalPeriods: number
  items: Fc3dMissingItem[]
}

export interface Fc3dSumTrendItem {
  issue: string
  sum: number
}

export interface Fc3dSumAnalysisResponse {
  lotteryType: string
  totalPeriods: number
  average: number
  distribution: Record<string, number>
  recentTrend: Fc3dSumTrendItem[]
}

export interface Fc3dOddEvenResponse {
  lotteryType: string
  oddCount: number
  evenCount: number
  pattern: string
  issue?: string
}

export interface Fc3dFeatureSummary {
  hotDigits: Record<string, number[]>
  sumAverage: number | null
  dominantOddEven: string | null
  notes: string[]
}

export interface Fc3dCandidateAnalysisItem {
  number: string
  score: number
  alignedSignals: string[]
  riskFlags: string[]
  comment: string
}

export interface Fc3dRecommendation {
  preferred: string | null
  rationale: string[]
  /** Statistical-only disclaimer — always shown in the UI. */
  disclaimer: string
}

/** Sprint 10-B: Top50 combination-pool coverage summary. */
export interface Fc3dScoreRange {
  min: number
  max: number
}

export interface Fc3dCandidateCoverage {
  total: number
  scoreRange: Fc3dScoreRange
  riskDistribution: Record<string, number>
}

export interface Fc3dAnalyzeResponse {
  lotteryType: string
  features: Fc3dFeatureSummary
  candidateAnalysis: Fc3dCandidateAnalysisItem[]
  recommendation: Fc3dRecommendation
  confidence: number
  modelName: string
  /** Sprint 10-B: additive, optional for backward compatibility. */
  candidateCoverage?: Fc3dCandidateCoverage
}

/** Sprint 10-B: a single ranked candidate from the Top50 combination pool. */
export interface Fc3dCombinationCandidate {
  number: string
  score: number
  rank: number
  reasons: string[]
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | string
}

export interface Fc3dCombinationResponse {
  lotteryType: string
  modelVersion: string
  totalCandidates: number
  candidates: Fc3dCombinationCandidate[]
}

export async function fetchFc3dHistory(page = 1, size = 20) {
  const res = await http.get<ApiResult<{ records: Fc3dDraw[]; total: number }>>('/api/v1/fc3d/history', {
    params: { page, size },
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export async function fetchFc3dAnalytics(): Promise<Fc3dAnalyticsSummary> {
  const res = await http.get<ApiResult<Fc3dAnalyticsSummary>>('/api/v1/fc3d/analytics/frequency')
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export async function fetchFc3dFrequency(): Promise<Fc3dFrequencyResponse> {
  const res = await http.get<ApiResult<Fc3dFrequencyResponse>>('/api/v1/fc3d/analytics/frequency')
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export async function fetchFc3dMissing(): Promise<Fc3dMissingResponse> {
  const res = await http.get<ApiResult<Fc3dMissingResponse>>('/api/v1/fc3d/analytics/missing')
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export async function fetchFc3dSumAnalysis(): Promise<Fc3dSumAnalysisResponse> {
  const res = await http.get<ApiResult<Fc3dSumAnalysisResponse>>('/api/v1/fc3d/analytics/sum')
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export async function fetchFc3dOddEven(): Promise<Fc3dOddEvenResponse> {
  const res = await http.get<ApiResult<Fc3dOddEvenResponse>>('/api/v1/fc3d/analytics/odd-even')
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

/**
 * Explains existing statistical candidates (frequency / missing / sum / odd-even).
 * Does NOT generate new numbers — statistical analysis only, no win guarantees.
 */
export async function analyzeFc3d(issue?: string, question?: string): Promise<Fc3dAnalyzeResponse> {
  const res = await http.post<ApiResult<Fc3dAnalyzeResponse>>('/api/v1/fc3d/analyze', null, {
    params: { issue, question },
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export async function predictFc3dRules(issue?: string) {
  const res = await http.get<ApiResult<Fc3dPredictResult>>('/api/v1/fc3d/predict/rules', {
    params: { issue },
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export async function predictFc3dAi(issue?: string) {
  const res = await http.get<ApiResult<Fc3dPredictResult>>('/api/v1/fc3d/predict/ai', {
    params: { issue },
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export async function predictFc3dHybrid(issue?: string) {
  const res = await http.post<ApiResult<Fc3dPredictResult>>('/api/v1/fc3d/predict', null, {
    params: { issue },
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

/**
 * Sprint 10-B: Top-N scored combination pool (default 50 of up to 1000 combinations).
 * Statistical analysis only — never generates numbers randomly, never guarantees any outcome.
 */
export async function fetchFc3dCombinations(): Promise<Fc3dCombinationResponse> {
  const res = await http.get<ApiResult<Fc3dCombinationResponse>>('/api/v1/fc3d/predict/combinations')
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export interface Fc3dBacktestResult {
  lotteryType: string
  totalPeriods: number
  minHistory: number
  topN: number
  top1HitRate: number
  top3HitRate: number
  top5HitRate: number
  /** Sprint 10-B: Top-N combination-pool hit rate. */
  top10HitRate: number
  top20HitRate: number
  top50HitRate: number
  sumAccuracy: number
  oddEvenAccuracy: number
  positionAccuracy: Record<string, number>
  note: string
}

/**
 * Walk-forward statistical evaluation of the existing candidate engine.
 * Evaluation only — does NOT generate new numbers.
 */
export async function runFc3dBacktestEvaluate(
  minHistory = 30,
  evalPeriods = 200,
  topN = 5
): Promise<Fc3dBacktestResult> {
  const res = await http.post<ApiResult<Fc3dBacktestResult>>('/api/v1/fc3d/backtest/evaluate', null, {
    params: { minHistory, evalPeriods, topN },
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

// ---- Sprint 11-D: walk-forward Top-N backtest detail ----

export interface Fc3dBacktestCandidate {
  number: string
  /** 1-based rank within the predicted Top-N for that period. */
  rank: number
  score: number
}

export interface Fc3dBacktestDetailItem {
  issue: string
  trainPeriods: number
  modelVersion: string
  predictedTop50: Fc3dBacktestCandidate[]
  actualNumber: string
  hit: boolean
  hitRank: number | null
  hitScore: number | null
  /** TOP1 | TOP10 | TOP20 | TOP50 | MISS */
  hitLevel: string
}

export interface Fc3dBacktestDetailResponse {
  lotteryType: string
  modelVersion: string
  evaluatedPeriods: number
  topN: number
  hitCount: number
  hitRate: number
  averageHitRank: number
  longestMissStreak: number
  details: Fc3dBacktestDetailItem[]
}

/**
 * Sprint 11-D: walk-forward Top-N detail — each period shows predicted Top-N from
 * history[0:i] only, the actual draw, and hit/rank. Evaluation only; never generates new numbers.
 */
export async function fetchFc3dBacktestDetails(
  minHistory = 30,
  evalPeriods = 200,
  topN = 50,
  modelVersion?: string
): Promise<Fc3dBacktestDetailResponse> {
  const res = await http.get<ApiResult<Fc3dBacktestDetailResponse>>('/api/v1/fc3d/backtest/details', {
    params: { minHistory, evalPeriods, topN, modelVersion: modelVersion || undefined },
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export interface Fc3dSyncStatus {
  source: string
  status: string
  fetchedCount: number
  newCount: number
  message: string
  latestIssue?: string
  startedAt?: string
  finishedAt?: string
  schedulerEnabled: boolean
  cron: string
  zone: string
  totalInDb: number
}

// ---- Sprint 10-C: model evaluation & parameter-optimization ----

export interface Fc3dModelEvaluationResult {
  modelName: string
  evaluatedPeriods: number
  top10HitRate: number
  top20HitRate: number
  top50HitRate: number
  improvementVsRandom: number
  improvementVsFrequency: number
}

/**
 * Statistical walk-forward comparison of the current model vs. a random baseline
 * and a frequency-only baseline. The random baseline is only ever used internally
 * as a benchmark — it is never surfaced as a recommendation.
 */
export async function compareFc3dModels(minHistory = 30, evalPeriods = 200) {
  const res = await http.post<ApiResult<Fc3dModelEvaluationResult[]>>(
    '/api/v1/fc3d/model-evaluation/compare',
    null,
    { params: { minHistory, evalPeriods } }
  )
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export interface Fc3dWeightOverride {
  frequency?: number
  missing?: number
  sum?: number
  oddEven?: number
  span?: number
}

export interface Fc3dExperimentMetrics {
  evaluatedPeriods: number
  top10HitRate: number
  top20HitRate: number
  top50HitRate: number
}

export interface Fc3dExperimentResult {
  experimentId: string
  modelVersion: string
  parameters: Record<string, number>
  metrics: Fc3dExperimentMetrics
  createdTime: string
}

/**
 * Batch parameter-optimization experiments. Disabled server-side by default
 * (lottery.fc3d.model.experiment.enabled=false) — never generates numbers, only
 * re-runs the walk-forward backtest with alternative weight configurations.
 */
export async function runFc3dModelExperiments(
  parameterSets: Fc3dWeightOverride[],
  minHistory = 30,
  evalPeriods = 200
) {
  const res = await http.post<ApiResult<Fc3dExperimentResult[]>>(
    '/api/v1/fc3d/model-evaluation/experiments',
    parameterSets,
    { params: { minHistory, evalPeriods } }
  )
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

// ---- Sprint 10-D: model operationalization loop (registry / selector / monitoring) ----

export interface Fc3dModelMetricsSummary {
  evaluatedPeriods: number
  top10HitRate: number
  top20HitRate: number
  top50HitRate: number
  improvementVsRandom: number
  improvementVsFrequency: number
  lastEvaluatedTime?: string
}

export interface Fc3dModelInfo {
  version: string
  /** ACTIVE (enabled) | INACTIVE (disabled — excluded from auto-selection) */
  status: 'ACTIVE' | 'INACTIVE' | string
  createdTime: string
  parameters: Record<string, number>
  metrics?: Fc3dModelMetricsSummary | null
  /** Whether this is the version Predict currently serves. */
  production: boolean
}

export interface Fc3dModelStatusResponse {
  activeModel: string | null
  lastEvaluation: string | null
  metrics?: Fc3dModelMetricsSummary | null
  health: 'GOOD' | 'WARN' | 'POOR' | 'UNKNOWN' | string
}

export interface Fc3dModelLeaderboardEntry {
  rank: number
  version: string
  status: string
  top10HitRate: number
  top20HitRate: number
  top50HitRate: number
  improvementVsRandom: number
  improvementVsFrequency: number
  lastEvaluatedTime?: string
}

export interface Fc3dModelSwitchRecord {
  fromVersion: string | null
  /** Null for a pure "deactivate" entry (no confirmed replacement was promoted). */
  toVersion: string | null
  /** Sprint 10-E: who triggered the switch/deactivation ("system" for the initial bootstrap). */
  operator?: string | null
  reason?: string | null
  switchedAt: string
}

export interface Fc3dModelSelectionResult {
  selectedModel: string | null
  reason: string[]
}

/** Sprint 10-D §5: model monitoring — currently active model + latest evaluation + health. */
export async function fetchFc3dModelStatus(): Promise<Fc3dModelStatusResponse> {
  const res = await http.get<ApiResult<Fc3dModelStatusResponse>>('/api/v1/fc3d/model/status')
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

/** Sprint 10-D §2: all registered model versions (parameters + status + latest metrics). */
export async function fetchFc3dModelRegistry(): Promise<Fc3dModelInfo[]> {
  const res = await http.get<ApiResult<Fc3dModelInfo[]>>('/api/v1/fc3d/model/registry')
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

/** Sprint 10-D §6: historical model leaderboard (rank / Top50 / Top20 / improvement ratios). */
export async function fetchFc3dModelLeaderboard(): Promise<Fc3dModelLeaderboardEntry[]> {
  const res = await http.get<ApiResult<Fc3dModelLeaderboardEntry[]>>('/api/v1/fc3d/model/leaderboard')
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

/** Sprint 10-D §6: audit log of production model switches (most recent first). */
export async function fetchFc3dModelSwitchLog(): Promise<Fc3dModelSwitchRecord[]> {
  const res = await http.get<ApiResult<Fc3dModelSwitchRecord[]>>('/api/v1/fc3d/model/switch-log')
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

/** Sprint 10-D §3: auto-selection recommendation — advisory only, never auto-applied. */
export async function fetchFc3dModelSelection(recentN = 20): Promise<Fc3dModelSelectionResult> {
  const res = await http.get<ApiResult<Fc3dModelSelectionResult>>('/api/v1/fc3d/model/select', {
    params: { recentN },
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

/** Sprint 10-D §2: registers a new candidate model version (e.g. from a promising experiment). */
export async function registerFc3dModel(modelVersion: string, parameters: Record<string, number>): Promise<Fc3dModelInfo> {
  const res = await http.post<ApiResult<Fc3dModelInfo>>('/api/v1/fc3d/model/register', {
    modelVersion,
    parameters,
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

/**
 * Sprint 10-D §4 / 10-E §2: promotes a registered version to be the production model Predict
 * serves (re-enabling it if disabled). Recorded in the switch-log audit trail.
 */
export async function activateFc3dModel(modelVersion: string, reason?: string): Promise<Fc3dModelInfo> {
  const res = await http.post<ApiResult<Fc3dModelInfo>>('/api/v1/fc3d/model/activate', null, {
    params: { modelVersion, reason },
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

/**
 * Sprint 10-E §2: disables a version — excluded from auto-selection AND from being resolved as
 * the automatic Predict model. Recorded in the switch-log audit trail.
 */
export async function deactivateFc3dModel(modelVersion: string, reason?: string): Promise<Fc3dModelInfo> {
  const res = await http.post<ApiResult<Fc3dModelInfo>>('/api/v1/fc3d/model/deactivate', null, {
    params: { modelVersion, reason },
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

// ---- Sprint 10-E: production persistence & monitoring (health / rollback) ----

export interface Fc3dModelHealthCheck {
  name: string
  value?: number | null
  status: 'GOOD' | 'WARNING' | 'FAILED' | string
  message?: string | null
}

export interface Fc3dModelHealthResponse {
  status: 'GOOD' | 'WARNING' | 'FAILED' | string
  model: string | null
  checks: Fc3dModelHealthCheck[]
}

export interface Fc3dModelRollbackSuggestion {
  rollbackSuggested: boolean
  current: string | null
  fallback: string | null
  reason: string[]
}

/** Sprint 10-E §3: comprehensive model health snapshot — GOOD / WARNING / FAILED. */
export async function fetchFc3dModelHealth(): Promise<Fc3dModelHealthResponse> {
  const res = await http.get<ApiResult<Fc3dModelHealthResponse>>('/api/v1/fc3d/model/health')
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

/**
 * Sprint 10-E §4: advisory-only rollback suggestion based on recent Top50 decline.
 * Never executes a rollback itself — an operator must still call activateFc3dModel().
 */
export async function fetchFc3dModelRollback(
  recentN = 5,
  declineThreshold = 0.2
): Promise<Fc3dModelRollbackSuggestion> {
  const res = await http.get<ApiResult<Fc3dModelRollbackSuggestion>>('/api/v1/fc3d/model/rollback', {
    params: { recentN, declineThreshold },
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

// ---- Sprint 11-A: multi-model ensemble fusion (weighted voting) ----

/** Sprint 11-A §3: one fused Top50 candidate — always a number already proposed by a member model. */
export interface Fc3dEnsembleCandidate {
  number: string
  /** 0-100, relative to the strongest fused candidate in this ensemble run. */
  ensembleScore: number
  /** How many member models proposed this number. */
  voteCount: number
  sourceModels: string[]
  reasons: string[]
}

export interface Fc3dEnsembleResponse {
  modelVersions: string[]
  /** Normalized (sums to 1) fusion weight actually applied to each participating model. */
  modelWeights: Record<string, number>
  topCandidates: Fc3dEnsembleCandidate[]
  /** "weighted-voting" — the only fusion strategy implemented so far. */
  fusionMethod: string
  createdTime: string
}

/**
 * Sprint 11-A §4: multi-model weighted-voting fusion. `modelVersions` optional — defaults to
 * every currently ACTIVE registered model. Fuses only ALREADY-COMPUTED candidates from each
 * model's own combination generator — never generates new numbers.
 */
export async function fetchFc3dEnsemble(modelVersions?: string[], topN = 50): Promise<Fc3dEnsembleResponse> {
  const res = await http.get<ApiResult<Fc3dEnsembleResponse>>('/api/v1/fc3d/predict/ensemble', {
    params: { modelVersions: modelVersions && modelVersions.length ? modelVersions.join(',') : undefined, topN },
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export interface Fc3dEnsembleBacktestResult {
  modelVersions: string[]
  /** The single model used as the baseline for comparison — normally the production model. */
  singleModelVersion: string | null
  evaluatedPeriods: number
  singleModelTop10HitRate: number
  singleModelTop20HitRate: number
  singleModelTop50HitRate: number
  ensembleTop10HitRate: number
  ensembleTop20HitRate: number
  ensembleTop50HitRate: number
  /** ensembleTop50HitRate - singleModelTop50HitRate */
  improvement: number
}

/**
 * Sprint 11-A §6: walk-forward comparison of the fused ensemble Top10/20/50 hit rates against
 * the current single production model's own Top10/20/50, over the same evaluation window.
 */
export async function runFc3dEnsembleBacktest(
  modelVersions?: string[],
  minHistory = 30,
  evalPeriods = 200
): Promise<Fc3dEnsembleBacktestResult> {
  const res = await http.post<ApiResult<Fc3dEnsembleBacktestResult>>(
    '/api/v1/fc3d/ensemble/backtest',
    null,
    { params: { modelVersions: modelVersions && modelVersions.length ? modelVersions.join(',') : undefined, minHistory, evalPeriods } }
  )
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

// ---- Sprint 11-B: Ensemble fusion-weight auto-optimization (grid search) ----

export interface Fc3dEnsembleOptimizationMetrics {
  top10: number
  top20: number
  top50: number
}

export interface Fc3dEnsembleOptimizationResponse {
  modelVersions: string[]
  /** Weights currently in effect (registry role-based default, or a previously applied override). */
  currentWeights: Record<string, number>
  /** Grid-search recommended weights — guaranteed to score >= currentWeights on Top50. */
  bestWeights: Record<string, number>
  before: Fc3dEnsembleOptimizationMetrics
  after: Fc3dEnsembleOptimizationMetrics
  /** after.top50 - before.top50 */
  improvement: number
  evaluatedPeriods: number
  /** "grid-search" — the only search strategy implemented so far. */
  searchMethod: string
  createdTime: string
}

/**
 * Sprint 11-B §2/§3: grid-search auto-optimization of Ensemble fusion weights over the same
 * walk-forward evaluation window. Advisory only — never applies `bestWeights` automatically;
 * call `applyFc3dEnsembleWeights()` explicitly (after human confirmation) to put it into effect.
 */
export async function optimizeFc3dEnsembleWeights(
  modelVersions?: string[],
  minHistory = 30,
  evalPeriods = 200
): Promise<Fc3dEnsembleOptimizationResponse> {
  const res = await http.post<ApiResult<Fc3dEnsembleOptimizationResponse>>(
    '/api/v1/fc3d/ensemble/optimize',
    null,
    { params: { modelVersions: modelVersions && modelVersions.length ? modelVersions.join(',') : undefined, minHistory, evalPeriods } }
  )
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

/**
 * Sprint 11-B §4: applies a (typically optimizer-recommended) fusion weight set for an EXACT
 * set of model versions. Manual, explicit action only — the UI must confirm with the operator
 * before calling this.
 */
export async function applyFc3dEnsembleWeights(
  modelVersions: string[],
  weights: Record<string, number>
): Promise<Record<string, number>> {
  const res = await http.post<ApiResult<Record<string, number>>>('/api/v1/fc3d/ensemble/apply-weights', {
    modelVersions,
    weights,
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

// ---- Sprint 11-C: Ensemble walk-forward performance report ----

export interface Fc3dEnsembleReportCoverage {
  top10: number
  top20: number
  top50: number
}

/** average/volatility/maxDrawdown of the Top50 hit rate across the report's calendar-quarter time windows. */
export interface Fc3dEnsembleReportStability {
  average: number
  volatility: number
  maxDrawdown: number
}

export interface Fc3dEnsembleReportTimeWindow {
  /** e.g. "2025 Q1". */
  label: string
  top50HitRate: number
  evaluatedPeriods: number
}

export interface Fc3dEnsembleReportResponse {
  /** e.g. "ensemble-v3" (single member) or "ensemble(v3+v3-exp-001)". */
  ensembleLabel: string
  modelVersions: string[]
  evaluatedPeriods: number
  coverage: Fc3dEnsembleReportCoverage
  stability: Fc3dEnsembleReportStability
  timeWindows: Fc3dEnsembleReportTimeWindow[]
  /** Overall status — the worst of healthChecks: GOOD | WARNING | FAILED. */
  health: string
  healthChecks: Fc3dModelHealthCheck[]
  createdTime: string
}

/**
 * Sprint 11-C §1: read-only walk-forward performance report for the Ensemble — Top10/20/50
 * coverage, stability across calendar-quarter time windows, and an overall health verdict.
 * Default evaluation window is the last 1000 periods. Advisory/observability only.
 */
export async function fetchFc3dEnsembleReport(
  modelVersions?: string[],
  minHistory = 30,
  evalPeriods = 1000
): Promise<Fc3dEnsembleReportResponse> {
  const res = await http.get<ApiResult<Fc3dEnsembleReportResponse>>('/api/v1/fc3d/ensemble/report', {
    params: { modelVersions: modelVersions && modelVersions.length ? modelVersions.join(',') : undefined, minHistory, evalPeriods },
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export async function fetchFc3dSyncStatus() {
  const res = await http.get<ApiResult<Fc3dSyncStatus>>('/api/v1/fc3d/sync/status')
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export async function triggerFc3dSync(full = false) {
  const res = await http.post<ApiResult<Fc3dSyncStatus>>('/api/v1/fc3d/sync/trigger', null, {
    params: { full },
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}
