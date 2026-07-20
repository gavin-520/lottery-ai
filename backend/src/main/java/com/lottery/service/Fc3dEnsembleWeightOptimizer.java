package com.lottery.service;

import com.lottery.dto.Fc3dCombinationResponse;
import com.lottery.dto.Fc3dEnsembleCandidate;
import com.lottery.dto.Fc3dEnsembleMemberInput;
import com.lottery.dto.Fc3dEnsembleOptimizationMetrics;
import com.lottery.dto.Fc3dEnsembleOptimizationResponse;
import com.lottery.dto.Fc3dFrequencyResponse;
import com.lottery.dto.Fc3dMissingResponse;
import com.lottery.dto.Fc3dSumAnalysisResponse;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.rule.fc3d.Fc3dCombinationGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sprint 11-B §1/§2: grid-search auto-optimization of Ensemble fusion weights, based on the
 * SAME walk-forward backtest contract already used elsewhere (train = {@code history.subList(0,
 * i)}, never future data). Only optimizes the fusion weights — never touches candidate
 * generation, {@code Fc3dModelConfig}, or the model registry, and NEVER auto-applies a result
 * (advisory only; see {@link Fc3dEnsembleWeightOverrideService#apply}).
 *
 * <p>Performance note: for a FIXED set of model versions, each walk-forward period's per-model
 * candidate list does not depend on the fusion weight being tried — only {@link
 * Fc3dEnsembleEngine#fuseAll} does. So every period's candidates are generated ONCE
 * ({@link #buildSnapshots}) and then re-fused many times (once per grid point), which is what
 * makes an exhaustive grid search tractable.</p>
 */
@Service
public class Fc3dEnsembleWeightOptimizer {

    public static final String SEARCH_METHOD_GRID_SEARCH = "grid-search";

    /** Sprint 11-B §2: "step: 0.05" — automatically coarsened for larger model counts, see {@link #chooseStep}. */
    private static final double DEFAULT_STEP = 0.05;
    private static final int DEFAULT_MIN_HISTORY = 30;
    private static final int DEFAULT_EVAL_PERIODS = 200;
    /** Safety cap so an operator can't accidentally request a combinatorial explosion. */
    private static final int MAX_GRID_COMBINATIONS = 20_000;

    private final Fc3dPredictService fc3dPredictService;
    private final Fc3dAnalyticsService fc3dAnalyticsService;
    private final Fc3dModelRegistryService fc3dModelRegistryService;
    private final Fc3dEnsemblePredictService fc3dEnsemblePredictService;
    private final Fc3dEnsembleEngine fc3dEnsembleEngine;

    public Fc3dEnsembleWeightOptimizer(Fc3dPredictService fc3dPredictService,
                                       Fc3dAnalyticsService fc3dAnalyticsService,
                                       Fc3dModelRegistryService fc3dModelRegistryService,
                                       Fc3dEnsemblePredictService fc3dEnsemblePredictService,
                                       Fc3dEnsembleEngine fc3dEnsembleEngine) {
        this.fc3dPredictService = fc3dPredictService;
        this.fc3dAnalyticsService = fc3dAnalyticsService;
        this.fc3dModelRegistryService = fc3dModelRegistryService;
        this.fc3dEnsemblePredictService = fc3dEnsemblePredictService;
        this.fc3dEnsembleEngine = fc3dEnsembleEngine;
    }

    public Fc3dEnsembleOptimizationResponse optimize(List<String> modelVersions, int minHistory, int evalPeriods) {
        List<String> versions = fc3dEnsemblePredictService.resolveVersions(modelVersions);
        int window = minHistory <= 0 ? DEFAULT_MIN_HISTORY : minHistory;
        int periods = evalPeriods <= 0 ? DEFAULT_EVAL_PERIODS : evalPeriods;

        Map<String, Double> currentWeights = fc3dEnsemblePredictService.resolveWeights(versions);

        Fc3dEnsembleOptimizationResponse response = new Fc3dEnsembleOptimizationResponse();
        response.setModelVersions(versions);
        response.setCurrentWeights(currentWeights);
        response.setSearchMethod(SEARCH_METHOD_GRID_SEARCH);
        response.setCreatedTime(LocalDateTime.now());

        if (versions.isEmpty()) {
            Fc3dEnsembleOptimizationMetrics empty = new Fc3dEnsembleOptimizationMetrics(0.0, 0.0, 0.0);
            response.setBestWeights(Map.of());
            response.setBefore(empty);
            response.setAfter(empty);
            response.setImprovement(0.0);
            return response;
        }

        List<PeriodSnapshot> snapshots = buildSnapshots(versions, window, periods);
        response.setEvaluatedPeriods(snapshots.size());

        Fc3dEnsembleOptimizationMetrics before = evaluate(snapshots, currentWeights);
        response.setBefore(before);

        Map<String, Double> bestWeights = currentWeights;
        Fc3dEnsembleOptimizationMetrics bestMetrics = before;

        if (versions.size() > 1 && !snapshots.isEmpty()) {
            double step = chooseStep(versions.size());
            for (Map<String, Double> candidate : gridCombinations(versions, step)) {
                Fc3dEnsembleOptimizationMetrics metrics = evaluate(snapshots, candidate);
                if (isBetter(metrics, bestMetrics)) {
                    bestWeights = candidate;
                    bestMetrics = metrics;
                }
            }
        }

        response.setBestWeights(bestWeights);
        response.setAfter(bestMetrics);
        response.setImprovement(round4(bestMetrics.getTop50() - before.getTop50()));
        return response;
    }

    /**
     * Walk-forward: builds each period's per-model candidate lists ONCE — train = {@code
     * history.subList(0, i)}, never future data. Package-private (not {@code private}) so
     * {@code Fc3dEnsembleWeightOptimizerTest} can verify the no-future-leakage / no-invented-
     * number invariants directly against the same snapshots {@link #optimize} actually uses —
     * same convention as {@code Fc3dModelEvaluationService#combinationRanking}.
     */
    List<PeriodSnapshot> buildSnapshots(List<String> versions, int window, int periods) {
        List<Fc3dDrawEntity> history = fc3dPredictService.listHistoryAsc();
        if (history == null || history.size() <= window) {
            return List.of();
        }
        int rangeStart = Math.max(window, history.size() - periods);
        int rangeEnd = history.size();

        Map<String, Fc3dCombinationGenerator> generators = new LinkedHashMap<>();
        for (String version : versions) {
            generators.put(version, new Fc3dCombinationGenerator(fc3dModelRegistryService.resolveConfig(version)));
        }

        List<PeriodSnapshot> snapshots = new ArrayList<>(Math.max(0, rangeEnd - rangeStart));
        for (int i = rangeStart; i < rangeEnd; i++) {
            List<Fc3dDrawEntity> train = history.subList(0, i);
            Fc3dDrawEntity actual = history.get(i);

            Fc3dFrequencyResponse frequency = fc3dAnalyticsService.getPositionFrequency(train);
            Fc3dMissingResponse missing = fc3dAnalyticsService.calculateMissing(train);
            Fc3dSumAnalysisResponse sumAnalysis = fc3dAnalyticsService.calculateSumAnalysis(train, 30);

            List<Fc3dEnsembleMemberInput> members = new ArrayList<>(versions.size());
            for (String version : versions) {
                Fc3dCombinationResponse combinationResponse = generators.get(version).generate(train, frequency, missing, sumAnalysis);
                members.add(new Fc3dEnsembleMemberInput(version, combinationResponse.getCandidates()));
            }
            snapshots.add(new PeriodSnapshot(numberOf(actual), members));
        }
        return snapshots;
    }

    Fc3dEnsembleOptimizationMetrics evaluate(List<PeriodSnapshot> snapshots, Map<String, Double> weights) {
        int hit10 = 0;
        int hit20 = 0;
        int hit50 = 0;
        for (PeriodSnapshot snapshot : snapshots) {
            List<Fc3dEnsembleCandidate> fused = fc3dEnsembleEngine.fuseAll(snapshot.members, weights);
            List<String> ranked = fused.stream().map(Fc3dEnsembleCandidate::getNumber).toList();
            if (containsWithinTop(ranked, snapshot.actualNumber, 10)) hit10++;
            if (containsWithinTop(ranked, snapshot.actualNumber, 20)) hit20++;
            if (containsWithinTop(ranked, snapshot.actualNumber, 50)) hit50++;
        }
        int total = snapshots.size();
        return new Fc3dEnsembleOptimizationMetrics(rate(hit10, total), rate(hit20, total), rate(hit50, total));
    }

    /** Top50 wins first, then Top20, then Top10 — ties keep the earlier (already-better-or-equal) candidate for determinism. */
    private boolean isBetter(Fc3dEnsembleOptimizationMetrics candidate, Fc3dEnsembleOptimizationMetrics current) {
        if (candidate.getTop50() != current.getTop50()) {
            return candidate.getTop50() > current.getTop50();
        }
        if (candidate.getTop20() != current.getTop20()) {
            return candidate.getTop20() > current.getTop20();
        }
        return candidate.getTop10() > current.getTop10();
    }

    /** Sprint 11-B §2: exhaustive grid over the (n-1)-simplex, {@code sum(weight) == 1} exactly (integer partition of 1/step). */
    private List<Map<String, Double>> gridCombinations(List<String> versions, double step) {
        int levels = (int) Math.max(1, Math.round(1.0 / step));
        List<Map<String, Double>> results = new ArrayList<>();
        int[] parts = new int[versions.size()];
        fillPartitions(parts, 0, levels, results, versions, step);
        return results;
    }

    private void fillPartitions(int[] parts, int index, int remaining, List<Map<String, Double>> out,
                                List<String> versions, double step) {
        if (index == parts.length - 1) {
            parts[index] = remaining;
            out.add(toWeightMap(parts, versions, step));
            return;
        }
        for (int value = 0; value <= remaining; value++) {
            parts[index] = value;
            fillPartitions(parts, index + 1, remaining - value, out, versions, step);
        }
    }

    private Map<String, Double> toWeightMap(int[] parts, List<String> versions, double step) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < versions.size(); i++) {
            map.put(versions.get(i), round4(parts[i] * step));
        }
        return map;
    }

    /** Coarsens the grid step automatically once the exhaustive combination count would exceed {@link #MAX_GRID_COMBINATIONS}. */
    private double chooseStep(int modelCount) {
        double step = DEFAULT_STEP;
        while (step < 0.5 && estimatedCombinations(modelCount, step) > MAX_GRID_COMBINATIONS) {
            step *= 2;
        }
        return step;
    }

    private long estimatedCombinations(int modelCount, double step) {
        long levels = Math.max(1, Math.round(1.0 / step));
        long n = modelCount - 1;
        // C(levels + n, n) — number of ways to partition `levels` steps among `modelCount` weights.
        long numerator = 1;
        long denominator = 1;
        for (long k = 1; k <= n; k++) {
            numerator *= (levels + k);
            denominator *= k;
        }
        return denominator == 0 ? Long.MAX_VALUE : numerator / denominator;
    }

    private boolean containsWithinTop(List<String> ranked, String number, int limit) {
        int max = Math.min(limit, ranked.size());
        for (int i = 0; i < max; i++) {
            if (number.equals(ranked.get(i))) {
                return true;
            }
        }
        return false;
    }

    private String numberOf(Fc3dDrawEntity e) {
        return String.format(Locale.ROOT, "%d%d%d", safe(e.getDigit1()), safe(e.getDigit2()), safe(e.getDigit3()));
    }

    private int safe(Integer value) {
        return value != null ? value : 0;
    }

    private double rate(int hits, int total) {
        if (total == 0) {
            return 0.0;
        }
        return Math.round((double) hits / total * 10000.0) / 10000.0;
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    /** Package-private (not {@code private}) — see {@link #buildSnapshots} javadoc. */
    static final class PeriodSnapshot {
        final String actualNumber;
        final List<Fc3dEnsembleMemberInput> members;

        PeriodSnapshot(String actualNumber, List<Fc3dEnsembleMemberInput> members) {
            this.actualNumber = actualNumber;
            this.members = members;
        }
    }
}
