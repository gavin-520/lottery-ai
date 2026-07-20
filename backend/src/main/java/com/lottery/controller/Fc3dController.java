package com.lottery.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lottery.common.Result;
import com.lottery.config.Fc3dModelConfig;
import com.lottery.dto.Fc3dAnalyzeResponse;
import com.lottery.dto.Fc3dBacktestDetailResponse;
import com.lottery.dto.Fc3dBacktestResponse;
import com.lottery.dto.Fc3dBacktestResult;
import com.lottery.dto.Fc3dCombinationResponse;
import com.lottery.dto.Fc3dEnsembleBacktestResult;
import com.lottery.dto.Fc3dEnsembleOptimizationResponse;
import com.lottery.dto.Fc3dEnsembleReportResponse;
import com.lottery.dto.Fc3dEnsembleResponse;
import com.lottery.dto.Fc3dEnsembleWeightApplyRequest;
import com.lottery.dto.Fc3dExperimentResult;
import com.lottery.dto.Fc3dFrequencyResponse;
import com.lottery.dto.Fc3dMissingResponse;
import com.lottery.dto.Fc3dModelEvaluationResult;
import com.lottery.dto.Fc3dModelHealthResponse;
import com.lottery.dto.Fc3dModelInfo;
import com.lottery.dto.Fc3dModelLeaderboardEntry;
import com.lottery.dto.Fc3dModelRegisterRequest;
import com.lottery.dto.Fc3dModelRollbackSuggestion;
import com.lottery.dto.Fc3dModelSelectionResult;
import com.lottery.dto.Fc3dModelStatusResponse;
import com.lottery.dto.Fc3dModelSwitchRecord;
import com.lottery.dto.Fc3dOddEvenResponse;
import com.lottery.dto.Fc3dPredictResponse;
import com.lottery.dto.Fc3dSumAnalysisResponse;
import com.lottery.dto.Fc3dSyncStatusResponse;
import com.lottery.dto.Fc3dWeightOverride;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.mapper.Fc3dDrawMapper;
import com.lottery.security.SecurityUtils;
import com.lottery.service.Fc3dAnalyticsService;
import com.lottery.service.Fc3dAnalyzeService;
import com.lottery.service.Fc3dBacktestDetailService;
import com.lottery.service.Fc3dBacktestService;
import com.lottery.service.Fc3dEnsembleBacktestService;
import com.lottery.service.Fc3dEnsemblePredictService;
import com.lottery.service.Fc3dEnsembleReportService;
import com.lottery.service.Fc3dEnsembleWeightOptimizer;
import com.lottery.service.Fc3dEnsembleWeightOverrideService;
import com.lottery.service.Fc3dModelEvaluationService;
import com.lottery.service.Fc3dModelHealthService;
import com.lottery.service.Fc3dModelMetricService;
import com.lottery.service.Fc3dModelRegistryService;
import com.lottery.service.Fc3dModelRollbackService;
import com.lottery.service.Fc3dModelSelector;
import com.lottery.service.Fc3dPredictService;
import com.lottery.service.Fc3dSyncService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/fc3d")
public class Fc3dController {

    private final Fc3dDrawMapper fc3dDrawMapper;
    private final Fc3dPredictService fc3dPredictService;
    private final Fc3dAnalyticsService fc3dAnalyticsService;
    private final Fc3dAnalyzeService fc3dAnalyzeService;
    private final Fc3dBacktestService fc3dBacktestService;
    private final Fc3dBacktestDetailService fc3dBacktestDetailService;
    private final Fc3dSyncService fc3dSyncService;
    private final Fc3dModelEvaluationService fc3dModelEvaluationService;
    private final Fc3dModelConfig fc3dModelConfig;
    private final Fc3dModelMetricService fc3dModelMetricService;
    private final Fc3dModelRegistryService fc3dModelRegistryService;
    private final Fc3dModelSelector fc3dModelSelector;
    private final Fc3dModelHealthService fc3dModelHealthService;
    private final Fc3dModelRollbackService fc3dModelRollbackService;
    private final Fc3dEnsemblePredictService fc3dEnsemblePredictService;
    private final Fc3dEnsembleBacktestService fc3dEnsembleBacktestService;
    private final Fc3dEnsembleWeightOptimizer fc3dEnsembleWeightOptimizer;
    private final Fc3dEnsembleWeightOverrideService fc3dEnsembleWeightOverrideService;
    private final Fc3dEnsembleReportService fc3dEnsembleReportService;

    public Fc3dController(Fc3dDrawMapper fc3dDrawMapper,
                          Fc3dPredictService fc3dPredictService,
                          Fc3dAnalyticsService fc3dAnalyticsService,
                          Fc3dAnalyzeService fc3dAnalyzeService,
                          Fc3dBacktestService fc3dBacktestService,
                          Fc3dBacktestDetailService fc3dBacktestDetailService,
                          Fc3dSyncService fc3dSyncService,
                          Fc3dModelEvaluationService fc3dModelEvaluationService,
                          Fc3dModelConfig fc3dModelConfig,
                          Fc3dModelMetricService fc3dModelMetricService,
                          Fc3dModelRegistryService fc3dModelRegistryService,
                          Fc3dModelSelector fc3dModelSelector,
                          Fc3dModelHealthService fc3dModelHealthService,
                          Fc3dModelRollbackService fc3dModelRollbackService,
                          Fc3dEnsemblePredictService fc3dEnsemblePredictService,
                          Fc3dEnsembleBacktestService fc3dEnsembleBacktestService,
                          Fc3dEnsembleWeightOptimizer fc3dEnsembleWeightOptimizer,
                          Fc3dEnsembleWeightOverrideService fc3dEnsembleWeightOverrideService,
                          Fc3dEnsembleReportService fc3dEnsembleReportService) {
        this.fc3dDrawMapper = fc3dDrawMapper;
        this.fc3dPredictService = fc3dPredictService;
        this.fc3dAnalyticsService = fc3dAnalyticsService;
        this.fc3dAnalyzeService = fc3dAnalyzeService;
        this.fc3dBacktestService = fc3dBacktestService;
        this.fc3dBacktestDetailService = fc3dBacktestDetailService;
        this.fc3dSyncService = fc3dSyncService;
        this.fc3dModelEvaluationService = fc3dModelEvaluationService;
        this.fc3dModelConfig = fc3dModelConfig;
        this.fc3dModelMetricService = fc3dModelMetricService;
        this.fc3dModelRegistryService = fc3dModelRegistryService;
        this.fc3dModelSelector = fc3dModelSelector;
        this.fc3dModelHealthService = fc3dModelHealthService;
        this.fc3dModelRollbackService = fc3dModelRollbackService;
        this.fc3dEnsemblePredictService = fc3dEnsemblePredictService;
        this.fc3dEnsembleBacktestService = fc3dEnsembleBacktestService;
        this.fc3dEnsembleWeightOptimizer = fc3dEnsembleWeightOptimizer;
        this.fc3dEnsembleWeightOverrideService = fc3dEnsembleWeightOverrideService;
        this.fc3dEnsembleReportService = fc3dEnsembleReportService;
    }

    @GetMapping("/history")
    public Result<Page<Fc3dDrawEntity>> history(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        Page<Fc3dDrawEntity> result = fc3dDrawMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Fc3dDrawEntity>().orderByDesc(Fc3dDrawEntity::getIssue)
        );
        return Result.ok(result);
    }

    @GetMapping("/analytics/frequency")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','USER')")
    public Result<Fc3dFrequencyResponse> analyticsFrequency() {
        return Result.ok(fc3dAnalyticsService.getPositionFrequency());
    }

    @GetMapping("/analytics/missing")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','USER')")
    public Result<Fc3dMissingResponse> analyticsMissing() {
        return Result.ok(fc3dAnalyticsService.calculateMissing());
    }

    @GetMapping("/analytics/sum")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','USER')")
    public Result<Fc3dSumAnalysisResponse> analyticsSum() {
        return Result.ok(fc3dAnalyticsService.calculateSumAnalysis());
    }

    @GetMapping("/analytics/odd-even")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','USER')")
    public Result<Fc3dOddEvenResponse> analyticsOddEven() {
        return Result.ok(fc3dAnalyticsService.calculateOddEvenAnalysis());
    }

    @GetMapping("/predict/rules")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<Fc3dPredictResponse> predictRules(@RequestParam(required = false) String issue) {
        return Result.ok(fc3dPredictService.predictByRules(issue));
    }

    @GetMapping("/predict/ai")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<Fc3dPredictResponse> predictAi(@RequestParam(required = false) String issue) {
        return Result.ok(fc3dPredictService.predictByAi(issue));
    }

    /** Statistical hybrid prediction (candidates + scores + reasons). */
    @GetMapping("/predict")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<Fc3dPredictResponse> predictGet(@RequestParam(required = false) String issue) {
        return Result.ok(fc3dPredictService.predictHybrid(issue));
    }

    @PostMapping("/predict")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<Fc3dPredictResponse> predict(@RequestParam(required = false) String issue) {
        return Result.ok(fc3dPredictService.predictHybrid(issue));
    }

    /**
     * Sprint 10-B: Top-N scored combination pool (default 50 of up to 1000 combinations).
     * Statistical analysis only — never generates numbers randomly, never guarantees any outcome.
     *
     * <p>Sprint 10-D: defaults to the currently ACTIVE registered model (Predict → ModelSelector
     * → Active Model → CombinationGenerator → Top50). Pass {@code modelVersion} to manually pin
     * a specific registered version instead.</p>
     */
    @GetMapping("/predict/combinations")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<Fc3dCombinationResponse> predictCombinations(
            @RequestParam(required = false) String modelVersion) {
        return Result.ok(fc3dPredictService.generateCombinations(modelVersion));
    }

    /**
     * Sprint 11-A §4: multi-model weighted-voting fusion. {@code modelVersions} optional
     * (comma-separated) — defaults to every currently ACTIVE registered model. Fuses only the
     * ALREADY-COMPUTED Top-N candidates each participating model's own
     * {@code Fc3dCombinationGenerator} produces — never generates new numbers, never changes
     * the existing {@code /predict/combinations} response.
     */
    @GetMapping("/predict/ensemble")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<Fc3dEnsembleResponse> predictEnsemble(
            @RequestParam(required = false) List<String> modelVersions,
            @RequestParam(required = false) Integer topN) {
        return Result.ok(fc3dEnsemblePredictService.predictEnsemble(modelVersions, topN));
    }

    /**
     * Explains existing statistical candidates (frequency / missing / sum / odd-even).
     * Does NOT generate new numbers — statistical analysis only, no win guarantees.
     */
    @PostMapping("/analyze")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','USER')")
    public Result<Fc3dAnalyzeResponse> analyze(@RequestParam(required = false) String issue,
                                               @RequestParam(required = false) String question) {
        return Result.ok(fc3dAnalyzeService.analyze(issue, question));
    }

    @PostMapping("/backtest/run")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<Fc3dBacktestResponse> backtest(@RequestParam(defaultValue = "10") int minHistory) {
        return Result.ok(fc3dBacktestService.run(minHistory));
    }

    /**
     * Walk-forward statistical evaluation: top1/top3/top5 hit rate + sum / odd-even / position
     * accuracy. Evaluates the existing candidate engine only — does not generate new numbers.
     */
    @PostMapping("/backtest/evaluate")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<Fc3dBacktestResult> backtestEvaluate(
            @RequestParam(defaultValue = "30") int minHistory,
            @RequestParam(defaultValue = "200") int evalPeriods,
            @RequestParam(defaultValue = "5") int topN) {
        return Result.ok(fc3dBacktestService.evaluate(minHistory, evalPeriods, topN));
    }

    /**
     * Sprint 11-D: walk-forward Top-N detail — for each evaluated period returns the model's
     * predicted Top-N (from {@code history.subList(0, i)} only), the actual draw, hit / rank /
     * score / level. Evaluation only; never generates numbers outside the existing combination
     * generator, never changes {@code /backtest/run} or {@code /backtest/evaluate}.
     */
    @GetMapping("/backtest/details")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<Fc3dBacktestDetailResponse> backtestDetails(
            @RequestParam(required = false) Integer minHistory,
            @RequestParam(required = false) Integer evalPeriods,
            @RequestParam(required = false) Integer topN,
            @RequestParam(required = false) String modelVersion) {
        return Result.ok(fc3dBacktestDetailService.evaluateDetails(minHistory, evalPeriods, topN, modelVersion));
    }

    /**
     * Sprint 10-C: walk-forward comparison of the current statistical model vs. a
     * random baseline and a frequency-only baseline. Evaluation only — the random
     * baseline is never surfaced as a recommendation, only as aggregate hit-rate stats.
     */
    @PostMapping("/model-evaluation/compare")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<List<Fc3dModelEvaluationResult>> compareModels(
            @RequestParam(defaultValue = "30") int minHistory,
            @RequestParam(defaultValue = "200") int evalPeriods) {
        List<Fc3dModelEvaluationResult> results = fc3dModelEvaluationService.compareModels(minHistory, evalPeriods);
        // Sprint 10-D §1: persist every comparison run for historical model comparison + auto-selection.
        for (Fc3dModelEvaluationResult result : results) {
            String modelVersion = Fc3dModelEvaluationService.MODEL_CURRENT.equals(result.getModelName())
                    ? fc3dModelConfig.getCombinationVersion()
                    : result.getModelName();
            Map<String, Double> parameters = Fc3dModelEvaluationService.MODEL_CURRENT.equals(result.getModelName())
                    ? fc3dModelConfig.toWeightMap()
                    : null;
            fc3dModelMetricService.record(modelVersion, result.getModelName(), result.getEvaluatedPeriods(),
                    result.getTop10HitRate(), result.getTop20HitRate(), result.getTop50HitRate(),
                    result.getImprovementVsRandom(), result.getImprovementVsFrequency(), parameters);
        }
        return Result.ok(results);
    }

    /**
     * Sprint 10-C: batch parameter-optimization experiments. Disabled by default via
     * {@code lottery.fc3d.model.experiment.enabled} — each experiment re-runs a full
     * walk-forward backtest and can be resource-intensive. Never generates numbers.
     */
    @PostMapping("/model-evaluation/experiments")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<List<Fc3dExperimentResult>> runExperiments(
            @RequestBody(required = false) List<Fc3dWeightOverride> parameterSets,
            @RequestParam(defaultValue = "30") int minHistory,
            @RequestParam(defaultValue = "200") int evalPeriods) {
        if (!fc3dModelConfig.isExperimentEnabled()) {
            return Result.fail(400, "参数实验功能未启用（lottery.fc3d.model.experiment.enabled=false）");
        }
        int max = Math.max(1, fc3dModelConfig.getExperimentMaxCombinations());
        if (parameterSets != null && parameterSets.size() > max) {
            return Result.fail(400, "实验参数组合数超过上限：" + max);
        }
        List<Fc3dExperimentResult> results = fc3dModelEvaluationService.runExperiments(parameterSets, minHistory, evalPeriods);
        // Sprint 10-D §1: persist each experiment's metrics for historical comparison + auto-selection.
        for (Fc3dExperimentResult result : results) {
            fc3dModelMetricService.record(result.getModelVersion(), "fc3d-combination-model-experiment",
                    result.getMetrics().getEvaluatedPeriods(),
                    result.getMetrics().getTop10HitRate(), result.getMetrics().getTop20HitRate(),
                    result.getMetrics().getTop50HitRate(), 0.0, 0.0, result.getParameters());
        }
        return Result.ok(results);
    }

    /**
     * Sprint 11-A §6: walk-forward comparison of the fused ensemble Top10/20/50 hit rates
     * against the current single production model's own Top10/20/50, over the same evaluation
     * window. {@code modelVersions} optional (comma-separated) — defaults to every ACTIVE
     * registered model, same resolution rule as {@code /predict/ensemble}.
     */
    @PostMapping("/ensemble/backtest")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<Fc3dEnsembleBacktestResult> ensembleBacktest(
            @RequestParam(required = false) List<String> modelVersions,
            @RequestParam(defaultValue = "30") int minHistory,
            @RequestParam(defaultValue = "200") int evalPeriods) {
        return Result.ok(fc3dEnsembleBacktestService.evaluate(modelVersions, minHistory, evalPeriods));
    }

    /**
     * Sprint 11-B §2/§3: grid-search auto-optimization of Ensemble fusion weights over the same
     * walk-forward evaluation window. Advisory only — {@code bestWeights} is never applied
     * automatically; call {@code /ensemble/apply-weights} explicitly to put it into effect.
     */
    @PostMapping("/ensemble/optimize")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<Fc3dEnsembleOptimizationResponse> optimizeEnsembleWeights(
            @RequestParam(required = false) List<String> modelVersions,
            @RequestParam(defaultValue = "30") int minHistory,
            @RequestParam(defaultValue = "200") int evalPeriods) {
        return Result.ok(fc3dEnsembleWeightOptimizer.optimize(modelVersions, minHistory, evalPeriods));
    }

    /**
     * Sprint 11-B §4: applies a (typically optimizer-recommended) fusion weight set for an EXACT
     * set of model versions. Manual, explicit action only — the UI must confirm with the
     * operator before calling this; never invoked automatically by the optimizer itself.
     */
    @PostMapping("/ensemble/apply-weights")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Double>> applyEnsembleWeights(@RequestBody Fc3dEnsembleWeightApplyRequest request) {
        if (request == null || request.getModelVersions() == null || request.getModelVersions().isEmpty()
                || request.getWeights() == null || request.getWeights().isEmpty()) {
            return Result.fail(400, "modelVersions / weights 不能为空");
        }
        fc3dEnsembleWeightOverrideService.apply(request.getModelVersions(), request.getWeights());
        return Result.ok(fc3dEnsembleWeightOverrideService.current().orElse(Map.of()));
    }

    /**
     * Sprint 11-C §1: read-only walk-forward performance report for the Ensemble — Top10/20/50
     * coverage, stability (average/volatility/max-drawdown of the Top50 hit rate across calendar
     * quarters) and an overall GOOD/WARNING/FAILED health verdict. {@code modelVersions} optional
     * (comma-separated) — same resolution rule as {@code /predict/ensemble}. Default evaluation
     * window is the last 1000 periods (far longer than the other ensemble endpoints' default 200).
     */
    @GetMapping("/ensemble/report")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<Fc3dEnsembleReportResponse> ensembleReport(
            @RequestParam(required = false) List<String> modelVersions,
            @RequestParam(required = false) Integer minHistory,
            @RequestParam(required = false) Integer evalPeriods) {
        return Result.ok(fc3dEnsembleReportService.generate(modelVersions, minHistory, evalPeriods));
    }

    // ------------------------------------------------------------------
    // Sprint 10-D: model registration, auto-selection, monitoring status.
    // Never generates numbers — pure model-lifecycle management on top of the
    // existing Fc3dCombinationGenerator / Fc3dModelEvaluationService.
    // ------------------------------------------------------------------

    /**
     * Sprint 10-D §5: model monitoring endpoint. Reflects the currently ACTIVE (production)
     * model and its most recently persisted walk-forward evaluation, if any.
     */
    @GetMapping("/model/status")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','USER')")
    public Result<Fc3dModelStatusResponse> modelStatus() {
        Optional<Fc3dModelInfo> active = fc3dModelRegistryService.getActiveModel();
        if (active.isEmpty()) {
            return Result.ok(new Fc3dModelStatusResponse(null, null, null, "UNKNOWN"));
        }
        Fc3dModelInfo info = active.get();
        String lastEvaluation = null;
        String health = "UNKNOWN";
        if (info.getMetrics() != null) {
            lastEvaluation = info.getMetrics().getLastEvaluatedTime() != null
                    ? info.getMetrics().getLastEvaluatedTime().toLocalDate().toString() : null;
            health = healthOf(info.getMetrics().getTop50HitRate());
        }
        return Result.ok(new Fc3dModelStatusResponse(info.getVersion(), lastEvaluation, info.getMetrics(), health));
    }

    /** Sprint 10-D §2: all registered model versions (parameters + status + latest metrics). */
    @GetMapping("/model/registry")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<List<Fc3dModelInfo>> modelRegistry() {
        return Result.ok(fc3dModelRegistryService.listModels());
    }

    /** Sprint 10-D §2: registers a new model version (candidate weights), e.g. from a promising experiment. */
    @PostMapping("/model/register")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<Fc3dModelInfo> registerModel(@RequestBody Fc3dModelRegisterRequest request) {
        if (request == null || request.getModelVersion() == null || request.getModelVersion().isBlank()) {
            return Result.fail(400, "modelVersion 不能为空");
        }
        return Result.ok(fc3dModelRegistryService.register(request.getModelVersion(), request.getParameters()));
    }

    /**
     * Sprint 10-D §4 / 10-E §2: promotes a registered version to be the production model
     * Predict serves automatically (and re-enables it if disabled). Manual, explicit action —
     * the auto-selection loop only recommends, never silently switches production traffic.
     * Every switch is durably recorded in {@code fc3d_model_switch_log}.
     */
    @PostMapping("/model/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Fc3dModelInfo> activateModel(@RequestParam String modelVersion,
                                                @RequestParam(required = false) String reason) {
        try {
            Fc3dModelInfo info = fc3dModelRegistryService.activate(modelVersion, currentOperator(), reason);
            return Result.ok(info);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return Result.fail(400, ex.getMessage());
        }
    }

    /**
     * Sprint 10-E §2: disables a version — excluded from auto-selection AND from being resolved
     * as the automatic Predict model, even if it is still marked production. Advisory-only
     * rollback suggestions (see {@code /model/rollback}) never call this automatically.
     */
    @PostMapping("/model/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Fc3dModelInfo> deactivateModel(@RequestParam String modelVersion,
                                                  @RequestParam(required = false) String reason) {
        try {
            Fc3dModelInfo info = fc3dModelRegistryService.deactivate(modelVersion, currentOperator(), reason);
            return Result.ok(info);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return Result.fail(400, ex.getMessage());
        }
    }

    private String currentOperator() {
        String username = SecurityUtils.currentUsername();
        return username != null && !username.isBlank() ? username : "system";
    }

    /**
     * Sprint 10-D §3: automatic model-selection recommendation, based on the most recent N
     * persisted walk-forward results. Advisory only — does not itself change the active model.
     */
    @GetMapping("/model/select")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<Fc3dModelSelectionResult> selectModel(@RequestParam(defaultValue = "20") int recentN) {
        return Result.ok(fc3dModelSelector.selectBest(recentN));
    }

    /** Sprint 10-D §6: historical model leaderboard (rank / Top50 / Top20 / improvement ratios). */
    @GetMapping("/model/leaderboard")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<List<Fc3dModelLeaderboardEntry>> modelLeaderboard() {
        List<Fc3dModelInfo> models = fc3dModelRegistryService.listModels();
        List<Fc3dModelInfo> evaluated = models.stream().filter(m -> m.getMetrics() != null).toList();

        List<Fc3dModelInfo> sorted = new ArrayList<>(evaluated);
        sorted.sort(Comparator
                .comparingDouble((Fc3dModelInfo m) -> m.getMetrics().getTop50HitRate()).reversed()
                .thenComparing(Comparator.comparingDouble((Fc3dModelInfo m) -> m.getMetrics().getTop20HitRate()).reversed())
                .thenComparing(Comparator.comparingDouble((Fc3dModelInfo m) -> m.getMetrics().getImprovementVsRandom()).reversed())
                .thenComparing(Fc3dModelInfo::getVersion));

        List<Fc3dModelLeaderboardEntry> leaderboard = new ArrayList<>(sorted.size());
        int rank = 1;
        for (Fc3dModelInfo m : sorted) {
            leaderboard.add(new Fc3dModelLeaderboardEntry(
                    rank++, m.getVersion(), m.getStatus(),
                    m.getMetrics().getTop10HitRate(), m.getMetrics().getTop20HitRate(), m.getMetrics().getTop50HitRate(),
                    m.getMetrics().getImprovementVsRandom(), m.getMetrics().getImprovementVsFrequency(),
                    m.getMetrics().getLastEvaluatedTime()));
        }
        return Result.ok(leaderboard);
    }

    /** Sprint 10-D §6 / 10-E §2: audit log of production model switches (most recent first). */
    @GetMapping("/model/switch-log")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<List<Fc3dModelSwitchRecord>> modelSwitchLog() {
        return Result.ok(fc3dModelRegistryService.getSwitchLog());
    }

    /**
     * Sprint 10-E §3: comprehensive model health snapshot — last-evaluation recency, Top50
     * level, model freshness, and production-model existence. GOOD / WARNING / FAILED.
     */
    @GetMapping("/model/health")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','USER')")
    public Result<Fc3dModelHealthResponse> modelHealth() {
        return Result.ok(fc3dModelHealthService.check());
    }

    /**
     * Sprint 10-E §4: advisory-only rollback suggestion, based on Top50 decline across the
     * most recent evaluations of the production model. Never executes a rollback itself.
     */
    @GetMapping("/model/rollback")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<Fc3dModelRollbackSuggestion> modelRollback(
            @RequestParam(defaultValue = "5") int recentN,
            @RequestParam(defaultValue = "0.20") double declineThreshold) {
        return Result.ok(fc3dModelRollbackService.check(recentN, declineThreshold));
    }

    private String healthOf(double top50HitRate) {
        if (top50HitRate >= 0.40) {
            return "GOOD";
        }
        if (top50HitRate >= 0.20) {
            return "WARN";
        }
        return "POOR";
    }

    @GetMapping("/sync/status")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','USER')")
    public Result<Fc3dSyncStatusResponse> syncStatus() {
        return Result.ok(fc3dSyncService.getStatus());
    }

    @PostMapping("/sync/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Fc3dSyncStatusResponse> syncTrigger(
            @RequestParam(defaultValue = "false") boolean full) {
        return Result.ok(fc3dSyncService.syncNow(full));
    }
}
