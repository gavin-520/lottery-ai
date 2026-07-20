package com.lottery.service;

import com.lottery.config.Fc3dModelConfig;
import com.lottery.dto.Fc3dCombinationCandidate;
import com.lottery.dto.Fc3dCombinationResponse;
import com.lottery.dto.Fc3dExperimentMetrics;
import com.lottery.dto.Fc3dExperimentResult;
import com.lottery.dto.Fc3dFrequencyResponse;
import com.lottery.dto.Fc3dMissingResponse;
import com.lottery.dto.Fc3dModelEvaluationResult;
import com.lottery.dto.Fc3dSumAnalysisResponse;
import com.lottery.dto.Fc3dWeightOverride;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.rule.fc3d.Fc3dCombinationGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

/**
 * Sprint 10-C: scientific evaluation and parameter-optimization loop for the FC3D
 * Top50 combination model.
 *
 * <p>Never generates or recommends numbers to end users — the random baseline used
 * here exists purely as a null-hypothesis benchmark inside walk-forward backtests and
 * is only ever surfaced as aggregate hit-rate statistics, never as a candidate list.
 * All evaluation is walk-forward: every period is scored using strictly
 * {@code history.subList(0, i)} — no future data is ever used.</p>
 */
@Service
public class Fc3dModelEvaluationService {

    public static final String MODEL_CURRENT = "fc3d-combination-model";
    public static final String MODEL_RANDOM = "random-baseline";
    public static final String MODEL_FREQUENCY = "frequency-only-baseline";

    private static final int DEFAULT_MIN_HISTORY = 30;
    private static final int DEFAULT_EVAL_PERIODS = 200;
    private static final int EVAL_TOP_N = 50;

    private final Fc3dPredictService fc3dPredictService;
    private final Fc3dAnalyticsService fc3dAnalyticsService;
    private final Fc3dModelConfig fc3dModelConfig;
    private final Fc3dCombinationGenerator currentModelGenerator;
    private final Fc3dCombinationGenerator frequencyOnlyGenerator;

    public Fc3dModelEvaluationService(Fc3dPredictService fc3dPredictService,
                                      Fc3dAnalyticsService fc3dAnalyticsService,
                                      Fc3dModelConfig fc3dModelConfig) {
        this.fc3dPredictService = fc3dPredictService;
        this.fc3dAnalyticsService = fc3dAnalyticsService;
        this.fc3dModelConfig = fc3dModelConfig;
        this.currentModelGenerator = new Fc3dCombinationGenerator(withEvalCandidateCount(fc3dModelConfig));
        this.frequencyOnlyGenerator = new Fc3dCombinationGenerator(frequencyOnlyConfig(fc3dModelConfig));
    }

    /**
     * Compares the current statistical model against a random baseline and a
     * frequency-only baseline, over the same walk-forward evaluation window.
     */
    public List<Fc3dModelEvaluationResult> compareModels(int minHistory, int evalPeriods) {
        List<Fc3dDrawEntity> history = fc3dPredictService.listHistoryAsc();

        Fc3dModelEvaluationResult current = evaluateModel(MODEL_CURRENT, history, minHistory, evalPeriods,
                train -> combinationRanking(currentModelGenerator, train));
        Fc3dModelEvaluationResult random = evaluateModel(MODEL_RANDOM, history, minHistory, evalPeriods,
                this::randomBaselineRanking);
        Fc3dModelEvaluationResult frequency = evaluateModel(MODEL_FREQUENCY, history, minHistory, evalPeriods,
                train -> combinationRanking(frequencyOnlyGenerator, train));

        applyImprovements(current, random, frequency);
        applyImprovements(random, random, frequency);
        applyImprovements(frequency, random, frequency);

        return List.of(current, random, frequency);
    }

    /**
     * Runs a batch of weight-parameter experiments, each evaluated with its own
     * independent walk-forward backtest (training/testing windows never overlap
     * with future data, same contract as {@link #compareModels}).
     */
    public List<Fc3dExperimentResult> runExperiments(List<Fc3dWeightOverride> parameterSets,
                                                     int minHistory, int evalPeriods) {
        if (parameterSets == null || parameterSets.isEmpty()) {
            return List.of();
        }

        List<Fc3dDrawEntity> history = fc3dPredictService.listHistoryAsc();
        List<Fc3dExperimentResult> results = new ArrayList<>(parameterSets.size());
        int index = 1;
        for (Fc3dWeightOverride override : parameterSets) {
            Fc3dModelConfig experimentConfig = buildExperimentConfig(override);
            Fc3dCombinationGenerator experimentGenerator = new Fc3dCombinationGenerator(experimentConfig);

            Fc3dModelEvaluationResult evaluation = evaluateModel("experiment", history, minHistory, evalPeriods,
                    train -> combinationRanking(experimentGenerator, train));

            Fc3dExperimentMetrics metrics = new Fc3dExperimentMetrics();
            metrics.setEvaluatedPeriods(evaluation.getEvaluatedPeriods());
            metrics.setTop10HitRate(evaluation.getTop10HitRate());
            metrics.setTop20HitRate(evaluation.getTop20HitRate());
            metrics.setTop50HitRate(evaluation.getTop50HitRate());

            Fc3dExperimentResult result = new Fc3dExperimentResult();
            result.setExperimentId(String.format(Locale.ROOT, "experiment-%03d", index++));
            result.setModelVersion(experimentConfig.getCombinationVersion());
            result.setParameters(toParameterMap(experimentConfig));
            result.setMetrics(metrics);
            result.setCreatedTime(LocalDateTime.now());
            results.add(result);
        }
        return results;
    }

    /**
     * Package-private: walk-forward evaluation core, shared by {@link #compareModels}
     * and {@link #runExperiments}. {@code rankingFn} must derive its ranking strictly
     * from the given train slice — no future data.
     */
    Fc3dModelEvaluationResult evaluateModel(String modelName, List<Fc3dDrawEntity> history,
                                            int minHistory, int evalPeriods,
                                            Function<List<Fc3dDrawEntity>, List<String>> rankingFn) {
        int window = Math.max(5, minHistory <= 0 ? DEFAULT_MIN_HISTORY : minHistory);
        int requestedPeriods = evalPeriods <= 0 ? DEFAULT_EVAL_PERIODS : evalPeriods;

        if (history == null || history.size() <= window) {
            return new Fc3dModelEvaluationResult(modelName, 0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        int rangeStart = Math.max(window, history.size() - requestedPeriods);
        int rangeEnd = history.size();

        int periods = 0;
        int hit10 = 0;
        int hit20 = 0;
        int hit50 = 0;
        for (int i = rangeStart; i < rangeEnd; i++) {
            List<Fc3dDrawEntity> train = history.subList(0, i);
            Fc3dDrawEntity actual = history.get(i);
            String actualNumber = numberOf(actual);
            List<String> ranked = rankingFn.apply(train);

            periods++;
            if (containsWithinTop(ranked, actualNumber, 10)) hit10++;
            if (containsWithinTop(ranked, actualNumber, 20)) hit20++;
            if (containsWithinTop(ranked, actualNumber, 50)) hit50++;
        }

        Fc3dModelEvaluationResult result = new Fc3dModelEvaluationResult();
        result.setModelName(modelName);
        result.setEvaluatedPeriods(periods);
        result.setTop10HitRate(rate(hit10, periods));
        result.setTop20HitRate(rate(hit20, periods));
        result.setTop50HitRate(rate(hit50, periods));
        return result;
    }

    /** Package-private: builds the current/frequency-only combination ranking for a train slice. */
    List<String> combinationRanking(Fc3dCombinationGenerator generator, List<Fc3dDrawEntity> train) {
        Fc3dFrequencyResponse frequency = fc3dAnalyticsService.getPositionFrequency(train);
        Fc3dMissingResponse missing = fc3dAnalyticsService.calculateMissing(train);
        Fc3dSumAnalysisResponse sumAnalysis = fc3dAnalyticsService.calculateSumAnalysis(train, 30);
        Fc3dCombinationResponse response = generator.generate(train, frequency, missing, sumAnalysis);
        List<Fc3dCombinationCandidate> candidates = response.getCandidates();
        if (candidates == null) {
            return List.of();
        }
        return candidates.stream().map(Fc3dCombinationCandidate::getNumber).toList();
    }

    /**
     * Package-private: deterministic "random" Top50 baseline. The shuffle seed is derived
     * purely from the train slice's own digits (never future data), so the SAME train slice
     * always yields the SAME "random" ranking — a stable, reproducible null-hypothesis
     * benchmark rather than true noise. Never exposed to end users as a recommendation.
     */
    List<String> randomBaselineRanking(List<Fc3dDrawEntity> train) {
        long seed = seedFrom(train);
        List<Integer> pool = new ArrayList<>(1000);
        for (int i = 0; i <= 999; i++) {
            pool.add(i);
        }
        Collections.shuffle(pool, new Random(seed));
        List<String> numbers = new ArrayList<>(EVAL_TOP_N);
        for (int i = 0; i < Math.min(EVAL_TOP_N, pool.size()); i++) {
            numbers.add(String.format(Locale.ROOT, "%03d", pool.get(i)));
        }
        return numbers;
    }

    private long seedFrom(List<Fc3dDrawEntity> train) {
        long seed = 17L;
        for (Fc3dDrawEntity d : train) {
            seed = seed * 31 + safe(d.getDigit1());
            seed = seed * 31 + safe(d.getDigit2());
            seed = seed * 31 + safe(d.getDigit3());
        }
        return seed * 31 + train.size();
    }

    private Fc3dModelConfig buildExperimentConfig(Fc3dWeightOverride override) {
        Fc3dModelConfig experimentConfig = withEvalCandidateCount(fc3dModelConfig);
        if (override != null) {
            if (override.getFrequency() != null) experimentConfig.setWeightFrequency(override.getFrequency());
            if (override.getMissing() != null) experimentConfig.setWeightMissing(override.getMissing());
            if (override.getSum() != null) experimentConfig.setWeightSum(override.getSum());
            if (override.getOddEven() != null) experimentConfig.setWeightOddEven(override.getOddEven());
            if (override.getSpan() != null) experimentConfig.setWeightSpan(override.getSpan());
        }
        experimentConfig.setCombinationVersion(fc3dModelConfig.getCombinationVersion() + "-experiment");
        return experimentConfig;
    }

    private Map<String, Double> toParameterMap(Fc3dModelConfig c) {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("frequency", c.getWeightFrequency());
        map.put("missing", c.getWeightMissing());
        map.put("sum", c.getWeightSum());
        map.put("oddEven", c.getWeightOddEven());
        map.put("span", c.getWeightSpan());
        return map;
    }

    private Fc3dModelConfig frequencyOnlyConfig(Fc3dModelConfig base) {
        Fc3dModelConfig config = withEvalCandidateCount(base);
        config.setWeightFrequency(1.0);
        config.setWeightMissing(0.0);
        config.setWeightSum(0.0);
        config.setWeightOddEven(0.0);
        config.setWeightSpan(0.0);
        config.setWeightAntiRepeatBonus(0.0);
        config.setWeightAntiRepeatPenalty(1.0);
        config.setCombinationVersion("frequency-only-v1");
        return config;
    }

    private Fc3dModelConfig withEvalCandidateCount(Fc3dModelConfig base) {
        Fc3dModelConfig copy = copyConfig(base);
        copy.setCandidateCount(Math.max(EVAL_TOP_N, base.getCandidateCount()));
        return copy;
    }

    private Fc3dModelConfig copyConfig(Fc3dModelConfig base) {
        Fc3dModelConfig copy = new Fc3dModelConfig();
        copy.setName(base.getName());
        copy.setVersion(base.getVersion());
        copy.setWeightFrequency(base.getWeightFrequency());
        copy.setWeightMissing(base.getWeightMissing());
        copy.setWeightSum(base.getWeightSum());
        copy.setWeightOddEven(base.getWeightOddEven());
        copy.setWeightAntiRepeatBonus(base.getWeightAntiRepeatBonus());
        copy.setWeightAntiRepeatPenalty(base.getWeightAntiRepeatPenalty());
        copy.setPoolPerPosition(base.getPoolPerPosition());
        copy.setRecentAvoidPeriods(base.getRecentAvoidPeriods());
        copy.setWeightSpan(base.getWeightSpan());
        copy.setCandidateCount(base.getCandidateCount());
        copy.setMaxPoolSize(base.getMaxPoolSize());
        copy.setCombinationVersion(base.getCombinationVersion());
        copy.setExperimentEnabled(base.isExperimentEnabled());
        copy.setExperimentMaxCombinations(base.getExperimentMaxCombinations());
        return copy;
    }

    private void applyImprovements(Fc3dModelEvaluationResult target,
                                   Fc3dModelEvaluationResult random,
                                   Fc3dModelEvaluationResult frequency) {
        target.setImprovementVsRandom(improvement(target.getTop50HitRate(), random.getTop50HitRate()));
        target.setImprovementVsFrequency(improvement(target.getTop50HitRate(), frequency.getTop50HitRate()));
    }

    private double improvement(double current, double baseline) {
        if (baseline <= 0.0) {
            return current > 0.0 ? 1.0 : 0.0;
        }
        return Math.round(((current - baseline) / baseline) * 10000.0) / 10000.0;
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

    private double rate(int hits, int total) {
        if (total == 0) {
            return 0.0;
        }
        return Math.round((double) hits / total * 10000.0) / 10000.0;
    }

    private int safe(Integer value) {
        return value != null ? value : 0;
    }
}
