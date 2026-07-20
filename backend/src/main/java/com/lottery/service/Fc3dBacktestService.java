package com.lottery.service;

import com.lottery.domain.LotteryType;
import com.lottery.dto.Fc3dBacktestResponse;
import com.lottery.dto.Fc3dBacktestResult;
import com.lottery.dto.Fc3dCandidate;
import com.lottery.dto.Fc3dCombinationCandidate;
import com.lottery.dto.Fc3dCombinationResponse;
import com.lottery.dto.Fc3dFrequencyResponse;
import com.lottery.dto.Fc3dMissingResponse;
import com.lottery.dto.Fc3dSumAnalysisResponse;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.rule.fc3d.Fc3dCombinationGenerator;
import com.lottery.rule.fc3d.Fc3dRuleEngine;
import com.lottery.util.Fc3dBallUtils;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FC3D backtest evaluation.
 * All predictions in a walk-forward step use ONLY {@code history.subList(0, i)} —
 * strictly the periods before the one being evaluated — to avoid future data leakage.
 * This service evaluates the existing statistical candidate engine; it never
 * generates or persists new numbers.
 */
@Service
public class Fc3dBacktestService {

    public static final int DEFAULT_TOP_N = 5;
    public static final int DEFAULT_EVAL_PERIODS = 200;
    public static final int DEFAULT_MIN_HISTORY = 30;

    private final Fc3dPredictService fc3dPredictService;
    private final Fc3dRuleEngine fc3dRuleEngine;
    private final Fc3dAnalyticsService fc3dAnalyticsService;
    private final Fc3dCombinationGenerator fc3dCombinationGenerator;

    public Fc3dBacktestService(Fc3dPredictService fc3dPredictService,
                               Fc3dRuleEngine fc3dRuleEngine,
                               Fc3dAnalyticsService fc3dAnalyticsService,
                               Fc3dCombinationGenerator fc3dCombinationGenerator) {
        this.fc3dPredictService = fc3dPredictService;
        this.fc3dRuleEngine = fc3dRuleEngine;
        this.fc3dAnalyticsService = fc3dAnalyticsService;
        this.fc3dCombinationGenerator = fc3dCombinationGenerator;
    }

    /**
     * Legacy simple walk-forward backtest (per-position top1 digit only).
     * Kept unchanged for backward compatibility with {@code POST /backtest/run}.
     */
    public Fc3dBacktestResponse run(int minHistory) {
        List<Fc3dDrawEntity> history = fc3dPredictService.listHistoryAsc();
        int window = Math.max(5, minHistory);
        if (history.size() <= window) {
            return new Fc3dBacktestResponse(
                    LotteryType.FC3D.name(), 0, 0, 0, 0, 0,
                    "历史不足，至少需要 " + (window + 1) + " 期"
            );
        }

        int periods = 0;
        int exactHits = 0;
        int digitHitsTotal = 0;
        int maxDigitHits = 0;

        for (int i = window; i < history.size(); i++) {
            List<Fc3dDrawEntity> train = history.subList(0, i);
            Fc3dDrawEntity actual = history.get(i);
            Fc3dRuleEngine.Fc3dScoreResult scores = fc3dRuleEngine.scoreAll(train);
            int p1 = top(scores.digit1Scores());
            int p2 = top(scores.digit2Scores());
            int p3 = top(scores.digit3Scores());

            int hits = 0;
            if (p1 == actual.getDigit1()) hits++;
            if (p2 == actual.getDigit2()) hits++;
            if (p3 == actual.getDigit3()) hits++;
            if (hits == 3) exactHits++;
            digitHitsTotal += hits;
            maxDigitHits = Math.max(maxDigitHits, hits);
            periods++;
        }

        return new Fc3dBacktestResponse(
                LotteryType.FC3D.name(),
                periods,
                periods == 0 ? 0 : (double) exactHits / periods,
                periods == 0 ? 0 : (double) digitHitsTotal / (periods * 3.0),
                periods == 0 ? 0 : (double) digitHitsTotal / periods,
                maxDigitHits,
                "walk-forward FC3D rule engine"
        );
    }

    /**
     * Walk-forward evaluation of the statistical candidate engine (Sprint 9-B/9-C).
     * Reports top1/top3/top5 hit rate plus sum / odd-even / per-position accuracy
     * of the top1 candidate. Statistical evaluation only — does not generate new numbers.
     */
    public Fc3dBacktestResult evaluate(int minHistory, int evalPeriods, int topN) {
        return evaluate(fc3dPredictService.listHistoryAsc(), minHistory, evalPeriods, topN);
    }

    public Fc3dBacktestResult evaluate(List<Fc3dDrawEntity> history, int minHistory, int evalPeriods, int topN) {
        int window = Math.max(5, minHistory <= 0 ? DEFAULT_MIN_HISTORY : minHistory);
        int n = topN <= 0 ? DEFAULT_TOP_N : topN;
        int requestedPeriods = evalPeriods <= 0 ? DEFAULT_EVAL_PERIODS : evalPeriods;

        if (history == null || history.size() <= window) {
            return emptyResult(window, n);
        }

        int rangeStart = Math.max(window, history.size() - requestedPeriods);
        int rangeEnd = history.size();

        int periods = 0;
        int top1Hits = 0;
        int top3Hits = 0;
        int top5Hits = 0;
        int top10Hits = 0;
        int top20Hits = 0;
        int top50Hits = 0;
        int sumHits = 0;
        int oddEvenHits = 0;
        int hundredsHits = 0;
        int tensHits = 0;
        int unitsHits = 0;

        for (int i = rangeStart; i < rangeEnd; i++) {
            PeriodResult result = evaluatePeriod(history, i, n);
            periods++;
            if (result.top1Hit()) top1Hits++;
            if (result.top3Hit()) top3Hits++;
            if (result.top5Hit()) top5Hits++;
            if (result.top10Hit()) top10Hits++;
            if (result.top20Hit()) top20Hits++;
            if (result.top50Hit()) top50Hits++;
            if (result.sumHit()) sumHits++;
            if (result.oddEvenHit()) oddEvenHits++;
            if (result.hundredsHit()) hundredsHits++;
            if (result.tensHit()) tensHits++;
            if (result.unitsHit()) unitsHits++;
        }

        Map<String, Double> positionAccuracy = new LinkedHashMap<>();
        positionAccuracy.put("hundreds", rate(hundredsHits, periods));
        positionAccuracy.put("tens", rate(tensHits, periods));
        positionAccuracy.put("units", rate(unitsHits, periods));

        Fc3dBacktestResult result = new Fc3dBacktestResult();
        result.setLotteryType(LotteryType.FC3D.name());
        result.setTotalPeriods(periods);
        result.setMinHistory(window);
        result.setTopN(n);
        result.setTop1HitRate(rate(top1Hits, periods));
        result.setTop3HitRate(rate(top3Hits, periods));
        result.setTop5HitRate(rate(top5Hits, periods));
        result.setTop10HitRate(rate(top10Hits, periods));
        result.setTop20HitRate(rate(top20Hits, periods));
        result.setTop50HitRate(rate(top50Hits, periods));
        result.setSumAccuracy(rate(sumHits, periods));
        result.setOddEvenAccuracy(rate(oddEvenHits, periods));
        result.setPositionAccuracy(positionAccuracy);
        result.setNote("walk-forward 统计回测：逐期仅使用 history[0,i) 训练，不含未来数据，仅评估统计模型");
        return result;
    }

    /**
     * Evaluates a single walk-forward period. Package-private so tests can verify
     * that changing data at indices &gt;= {@code index} never changes the result
     * for {@code index} (no future data leakage).
     */
    PeriodResult evaluatePeriod(List<Fc3dDrawEntity> history, int index, int topN) {
        List<Fc3dDrawEntity> train = history.subList(0, index);
        Fc3dDrawEntity actual = history.get(index);

        List<Fc3dCandidate> candidates = predictCandidates(train, topN);
        String actualNumber = String.format("%d%d%d",
                safe(actual.getDigit1()), safe(actual.getDigit2()), safe(actual.getDigit3()));

        boolean top1 = !candidates.isEmpty() && actualNumber.equals(candidates.get(0).getNumber());
        boolean top3 = containsWithinTop(candidates, actualNumber, 3);
        boolean top5 = containsWithinTop(candidates, actualNumber, 5);

        // Sprint 10-B: Top-N combination-pool coverage, built strictly from the same train slice.
        List<Fc3dCombinationCandidate> combinationCandidates = predictCombinationCandidates(train);
        boolean top10 = containsWithinTopCombination(combinationCandidates, actualNumber, 10);
        boolean top20 = containsWithinTopCombination(combinationCandidates, actualNumber, 20);
        boolean top50 = containsWithinTopCombination(combinationCandidates, actualNumber, 50);

        boolean sumHit = false;
        boolean oddEvenHit = false;
        boolean hundredsHit = false;
        boolean tensHit = false;
        boolean unitsHit = false;

        if (!candidates.isEmpty()) {
            Fc3dCandidate best = candidates.get(0);
            String number = best.getNumber();
            if (number != null && number.length() == 3) {
                int bd1 = Character.getNumericValue(number.charAt(0));
                int bd2 = Character.getNumericValue(number.charAt(1));
                int bd3 = Character.getNumericValue(number.charAt(2));

                int actualSum = actual.getSumValue() != null
                        ? actual.getSumValue()
                        : Fc3dBallUtils.sum(safe(actual.getDigit1()), safe(actual.getDigit2()), safe(actual.getDigit3()));
                sumHit = Fc3dBallUtils.sum(bd1, bd2, bd3) == actualSum;

                String actualOddEven = actual.getOddEvenPattern() != null
                        ? actual.getOddEvenPattern()
                        : Fc3dBallUtils.oddEvenPattern(safe(actual.getDigit1()), safe(actual.getDigit2()), safe(actual.getDigit3()));
                oddEvenHit = Fc3dBallUtils.oddEvenPattern(bd1, bd2, bd3).equalsIgnoreCase(actualOddEven);

                hundredsHit = bd1 == safe(actual.getDigit1());
                tensHit = bd2 == safe(actual.getDigit2());
                unitsHit = bd3 == safe(actual.getDigit3());
            }
        }

        return new PeriodResult(top1, top3, top5, top10, top20, top50,
                sumHit, oddEvenHit, hundredsHit, tensHit, unitsHit);
    }

    /** Package-private: builds candidates strictly from the given train slice (no leakage). */
    List<Fc3dCandidate> predictCandidates(List<Fc3dDrawEntity> train, int topN) {
        Fc3dFrequencyResponse frequency = fc3dAnalyticsService.getPositionFrequency(train);
        Fc3dMissingResponse missing = fc3dAnalyticsService.calculateMissing(train);
        Fc3dSumAnalysisResponse sumAnalysis = fc3dAnalyticsService.calculateSumAnalysis(train, 30);
        List<Fc3dCandidate> candidates = fc3dRuleEngine.generateCandidates(train, frequency, missing, sumAnalysis, topN);
        return candidates != null ? candidates : List.of();
    }

    /**
     * Sprint 10-B: package-private, builds the Top-N combination pool strictly from the
     * given train slice (no leakage) — same contract as {@link #predictCandidates}.
     */
    List<Fc3dCombinationCandidate> predictCombinationCandidates(List<Fc3dDrawEntity> train) {
        Fc3dFrequencyResponse frequency = fc3dAnalyticsService.getPositionFrequency(train);
        Fc3dMissingResponse missing = fc3dAnalyticsService.calculateMissing(train);
        Fc3dSumAnalysisResponse sumAnalysis = fc3dAnalyticsService.calculateSumAnalysis(train, 30);
        Fc3dCombinationResponse response = fc3dCombinationGenerator.generate(train, frequency, missing, sumAnalysis);
        return response != null && response.getCandidates() != null ? response.getCandidates() : List.of();
    }

    private boolean containsWithinTop(List<Fc3dCandidate> candidates, String number, int limit) {
        int max = Math.min(limit, candidates.size());
        for (int i = 0; i < max; i++) {
            if (number.equals(candidates.get(i).getNumber())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsWithinTopCombination(List<Fc3dCombinationCandidate> candidates, String number, int limit) {
        int max = Math.min(limit, candidates.size());
        for (int i = 0; i < max; i++) {
            if (number.equals(candidates.get(i).getNumber())) {
                return true;
            }
        }
        return false;
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

    private Fc3dBacktestResult emptyResult(int window, int topN) {
        Fc3dBacktestResult result = new Fc3dBacktestResult();
        result.setLotteryType(LotteryType.FC3D.name());
        result.setTotalPeriods(0);
        result.setMinHistory(window);
        result.setTopN(topN);
        result.setTop1HitRate(0);
        result.setTop3HitRate(0);
        result.setTop5HitRate(0);
        result.setTop10HitRate(0);
        result.setTop20HitRate(0);
        result.setTop50HitRate(0);
        result.setSumAccuracy(0);
        result.setOddEvenAccuracy(0);
        Map<String, Double> positionAccuracy = new LinkedHashMap<>();
        positionAccuracy.put("hundreds", 0.0);
        positionAccuracy.put("tens", 0.0);
        positionAccuracy.put("units", 0.0);
        result.setPositionAccuracy(positionAccuracy);
        result.setNote("历史不足，至少需要 " + (window + 1) + " 期");
        return result;
    }

    private int top(Map<Integer, Double> scores) {
        return scores.entrySet().stream()
                .max(Comparator.comparingDouble(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(0);
    }

    record PeriodResult(boolean top1Hit, boolean top3Hit, boolean top5Hit,
                        boolean top10Hit, boolean top20Hit, boolean top50Hit, boolean sumHit,
                        boolean oddEvenHit, boolean hundredsHit, boolean tensHit, boolean unitsHit) {}
}
