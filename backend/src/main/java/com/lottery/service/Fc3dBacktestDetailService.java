package com.lottery.service;

import com.lottery.config.Fc3dModelConfig;
import com.lottery.domain.LotteryType;
import com.lottery.dto.Fc3dBacktestCandidate;
import com.lottery.dto.Fc3dBacktestDetailItem;
import com.lottery.dto.Fc3dBacktestDetailResponse;
import com.lottery.dto.Fc3dCombinationCandidate;
import com.lottery.dto.Fc3dCombinationResponse;
import com.lottery.dto.Fc3dFrequencyResponse;
import com.lottery.dto.Fc3dMissingResponse;
import com.lottery.dto.Fc3dSumAnalysisResponse;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.rule.fc3d.Fc3dCombinationGenerator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sprint 11-D: walk-forward Top-N backtest DETAIL — one row per evaluated period, showing the
 * candidates the model produced from {@code history.subList(0, i)}, the actual draw at index
 * {@code i}, and whether / where it hit. Never invents numbers; never reads future data.
 */
@Service
public class Fc3dBacktestDetailService {

    public static final int DEFAULT_MIN_HISTORY = 30;
    public static final int DEFAULT_EVAL_PERIODS = 200;
    public static final int DEFAULT_TOP_N = 50;

    private final Fc3dPredictService fc3dPredictService;
    private final Fc3dAnalyticsService fc3dAnalyticsService;
    private final Fc3dModelRegistryService fc3dModelRegistryService;

    public Fc3dBacktestDetailService(Fc3dPredictService fc3dPredictService,
                                    Fc3dAnalyticsService fc3dAnalyticsService,
                                    Fc3dModelRegistryService fc3dModelRegistryService) {
        this.fc3dPredictService = fc3dPredictService;
        this.fc3dAnalyticsService = fc3dAnalyticsService;
        this.fc3dModelRegistryService = fc3dModelRegistryService;
    }

    public Fc3dBacktestDetailResponse evaluateDetails(Integer minHistory, Integer evalPeriods,
                                                      Integer topN, String modelVersion) {
        return evaluateDetails(fc3dPredictService.listHistoryAsc(), minHistory, evalPeriods, topN, modelVersion);
    }

    /**
     * Package-visible overload so tests can inject a fixture history and verify the
     * no-future-leakage / hit-rate contracts without a live database.
     */
    Fc3dBacktestDetailResponse evaluateDetails(List<Fc3dDrawEntity> history, Integer minHistory,
                                               Integer evalPeriods, Integer topN, String modelVersion) {
        int window = Math.max(5, minHistory == null || minHistory <= 0 ? DEFAULT_MIN_HISTORY : minHistory);
        int periods = evalPeriods == null || evalPeriods <= 0 ? DEFAULT_EVAL_PERIODS : evalPeriods;
        int n = topN == null || topN <= 0 ? DEFAULT_TOP_N : topN;

        Fc3dModelConfig config = withCandidateCount(fc3dModelRegistryService.resolveConfig(modelVersion), n);
        String resolvedVersion = config.getCombinationVersion();
        Fc3dCombinationGenerator generator = new Fc3dCombinationGenerator(config);

        Fc3dBacktestDetailResponse response = new Fc3dBacktestDetailResponse();
        response.setLotteryType(LotteryType.FC3D.name());
        response.setModelVersion(resolvedVersion);
        response.setTopN(n);

        if (history == null || history.size() <= window) {
            response.setEvaluatedPeriods(0);
            response.setHitCount(0);
            response.setHitRate(0.0);
            response.setAverageHitRank(0.0);
            response.setLongestMissStreak(0);
            response.setDetails(List.of());
            return response;
        }

        int rangeStart = Math.max(window, history.size() - periods);
        int rangeEnd = history.size();

        List<Fc3dBacktestDetailItem> details = new ArrayList<>(Math.max(0, rangeEnd - rangeStart));
        int hitCount = 0;
        long hitRankSum = 0;
        int currentMiss = 0;
        int longestMiss = 0;

        for (int i = rangeStart; i < rangeEnd; i++) {
            List<Fc3dDrawEntity> train = history.subList(0, i);
            Fc3dDrawEntity actual = history.get(i);
            String actualNumber = numberOf(actual);

            List<Fc3dBacktestCandidate> predicted = predictTopN(generator, train, n);
            Fc3dBacktestDetailItem item = matchPeriod(actual.getIssue(), train.size(), resolvedVersion,
                    predicted, actualNumber);
            details.add(item);

            if (item.isHit()) {
                hitCount++;
                hitRankSum += item.getHitRank() != null ? item.getHitRank() : 0;
                currentMiss = 0;
            } else {
                currentMiss++;
                longestMiss = Math.max(longestMiss, currentMiss);
            }
        }

        int evaluated = details.size();
        response.setEvaluatedPeriods(evaluated);
        response.setHitCount(hitCount);
        response.setHitRate(rate(hitCount, evaluated));
        response.setAverageHitRank(hitCount == 0 ? 0.0 : round4((double) hitRankSum / hitCount));
        response.setLongestMissStreak(longestMiss);
        response.setDetails(details);
        return response;
    }

    /**
     * Builds Top-N candidates from a SINGLE train slice — never the full history.
     * Package-private so leakage tests can call the same prediction path used by
     * {@link #evaluateDetails}.
     */
    List<Fc3dBacktestCandidate> predictTopN(Fc3dCombinationGenerator generator,
                                            List<Fc3dDrawEntity> train, int topN) {
        Fc3dFrequencyResponse frequency = fc3dAnalyticsService.getPositionFrequency(train);
        Fc3dMissingResponse missing = fc3dAnalyticsService.calculateMissing(train);
        Fc3dSumAnalysisResponse sumAnalysis = fc3dAnalyticsService.calculateSumAnalysis(train, 30);
        Fc3dCombinationResponse response = generator.generate(train, frequency, missing, sumAnalysis);

        List<Fc3dCombinationCandidate> source = response.getCandidates() != null
                ? response.getCandidates() : List.of();
        int limit = Math.min(topN, source.size());
        List<Fc3dBacktestCandidate> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Fc3dCombinationCandidate c = source.get(i);
            out.add(new Fc3dBacktestCandidate(c.getNumber(), i + 1, c.getScore()));
        }
        return out;
    }

    private Fc3dBacktestDetailItem matchPeriod(String issue, int trainPeriods, String modelVersion,
                                               List<Fc3dBacktestCandidate> predicted, String actualNumber) {
        Fc3dBacktestDetailItem item = new Fc3dBacktestDetailItem();
        item.setIssue(issue);
        item.setTrainPeriods(trainPeriods);
        item.setModelVersion(modelVersion);
        item.setPredictedTop50(predicted);
        item.setActualNumber(actualNumber);

        for (Fc3dBacktestCandidate candidate : predicted) {
            if (actualNumber.equals(candidate.getNumber())) {
                item.setHit(true);
                item.setHitRank(candidate.getRank());
                item.setHitScore(candidate.getScore());
                item.setHitLevel(hitLevel(candidate.getRank()));
                return item;
            }
        }
        item.setHit(false);
        item.setHitRank(null);
        item.setHitScore(null);
        item.setHitLevel("MISS");
        return item;
    }

    private String hitLevel(int hitRank) {
        if (hitRank <= 1) return "TOP1";
        if (hitRank <= 10) return "TOP10";
        if (hitRank <= 20) return "TOP20";
        return "TOP50";
    }

    private Fc3dModelConfig withCandidateCount(Fc3dModelConfig base, int topN) {
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
        copy.setCandidateCount(Math.max(topN, base.getCandidateCount()));
        copy.setMaxPoolSize(base.getMaxPoolSize());
        copy.setCombinationVersion(base.getCombinationVersion());
        copy.setExperimentEnabled(base.isExperimentEnabled());
        copy.setExperimentMaxCombinations(base.getExperimentMaxCombinations());
        return copy;
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
        return round4((double) hits / total);
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
