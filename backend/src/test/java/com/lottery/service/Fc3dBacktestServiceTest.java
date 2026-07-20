package com.lottery.service;

import com.lottery.config.Fc3dModelConfig;
import com.lottery.dto.Fc3dBacktestResult;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.rule.fc3d.Fc3dCombinationGenerator;
import com.lottery.rule.fc3d.Fc3dRuleEngine;
import com.lottery.util.Fc3dBallUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class Fc3dBacktestServiceTest {

    @Mock
    private Fc3dPredictService fc3dPredictService;

    private Fc3dBacktestService backtestService;

    @BeforeEach
    void setUp() {
        Fc3dModelConfig config = new Fc3dModelConfig();
        Fc3dRuleEngine ruleEngine = new Fc3dRuleEngine(config);
        Fc3dAnalyticsService analyticsService = new Fc3dAnalyticsService(null);
        Fc3dCombinationGenerator combinationGenerator = new Fc3dCombinationGenerator(config);
        backtestService = new Fc3dBacktestService(fc3dPredictService, ruleEngine, analyticsService, combinationGenerator);
    }

    @Test
    void evaluate_returnsRatesWithinValidRange_forFixedHistory() {
        List<Fc3dDrawEntity> history = buildHistory(80);

        Fc3dBacktestResult result = backtestService.evaluate(history, 20, 40, 5);

        assertTrue(result.getTotalPeriods() > 0);
        assertRateInRange(result.getTop1HitRate());
        assertRateInRange(result.getTop3HitRate());
        assertRateInRange(result.getTop5HitRate());
        assertRateInRange(result.getTop10HitRate());
        assertRateInRange(result.getTop20HitRate());
        assertRateInRange(result.getTop50HitRate());
        assertRateInRange(result.getSumAccuracy());
        assertRateInRange(result.getOddEvenAccuracy());
        result.getPositionAccuracy().values().forEach(this::assertRateInRange);
        // top1 <= top3 <= top5 by construction (superset containment)
        assertTrue(result.getTop1HitRate() <= result.getTop3HitRate() + 1e-9);
        assertTrue(result.getTop3HitRate() <= result.getTop5HitRate() + 1e-9);
    }

    @Test
    void evaluate_top10Top20Top50HitRates_areNonDecreasing_asCoverageExpands() {
        // Sprint 10-B: expanding coverage (10 -> 20 -> 50 candidates from the same
        // combination pool) must never decrease the hit rate (superset containment).
        List<Fc3dDrawEntity> history = buildHistory(80);

        Fc3dBacktestResult result = backtestService.evaluate(history, 20, 40, 5);

        assertRateInRange(result.getTop10HitRate());
        assertRateInRange(result.getTop20HitRate());
        assertRateInRange(result.getTop50HitRate());
        assertTrue(result.getTop10HitRate() <= result.getTop20HitRate() + 1e-9,
                "top10 hit rate must be <= top20 hit rate");
        assertTrue(result.getTop20HitRate() <= result.getTop50HitRate() + 1e-9,
                "top20 hit rate must be <= top50 hit rate");
    }

    @Test
    void evaluate_respectsMinHistory_whenHistoryTooShort() {
        List<Fc3dDrawEntity> history = buildHistory(10);

        Fc3dBacktestResult result = backtestService.evaluate(history, 20, 40, 5);

        assertEquals(0, result.getTotalPeriods());
        assertEquals(0.0, result.getTop1HitRate());
        assertTrue(result.getNote().contains("历史不足"));
    }

    @Test
    void evaluate_limitsEvaluatedPeriods_toEvalPeriodsParameter() {
        List<Fc3dDrawEntity> history = buildHistory(80);

        Fc3dBacktestResult result = backtestService.evaluate(history, 20, 10, 5);

        assertEquals(10, result.getTotalPeriods());
    }

    @Test
    void evaluatePeriod_neverUsesFutureData_sameTrainPrefixYieldsSameResult() {
        // historyA: 40 deterministic periods.
        List<Fc3dDrawEntity> historyA = buildHistory(40);

        // historyB: identical for indices [0, 35], but a wildly different, extended
        // "future" beyond index 35 (indices 36..59). Index 35 itself (the "actual"
        // draw being evaluated) is unchanged so only genuine future leakage would
        // change the outcome of evaluating period 35.
        List<Fc3dDrawEntity> historyB = new ArrayList<>(historyA.subList(0, 36));
        for (int i = 36; i < 60; i++) {
            historyB.add(extremeDraw(String.format("%07d", i + 1)));
        }

        Fc3dBacktestService.PeriodResult resultA = backtestService.evaluatePeriod(historyA, 35, 5);
        Fc3dBacktestService.PeriodResult resultB = backtestService.evaluatePeriod(historyB, 35, 5);

        assertEquals(resultA, resultB, "future data (indices > 35) must not influence the prediction for period 35");
    }

    @Test
    void predictCandidates_onlyUsesProvidedTrainSlice_notBeyondIt() {
        List<Fc3dDrawEntity> historyA = buildHistory(30);
        List<Fc3dDrawEntity> historyB = new ArrayList<>(historyA);
        // Append extreme future rows that must never affect a candidate list built
        // from a train slice ending before those rows exist.
        for (int i = 30; i < 50; i++) {
            historyB.add(extremeDraw(String.format("%07d", i + 1)));
        }

        var candidatesFromA = backtestService.predictCandidates(historyA.subList(0, 25), 5);
        var candidatesFromB = backtestService.predictCandidates(historyB.subList(0, 25), 5);

        assertEquals(candidatesFromA, candidatesFromB);
    }

    private void assertRateInRange(double rate) {
        assertTrue(rate >= 0.0 && rate <= 1.0, "rate out of [0,1] range: " + rate);
    }

    private List<Fc3dDrawEntity> buildHistory(int n) {
        List<Fc3dDrawEntity> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int d1 = i % 10;
            int d2 = (i * 3) % 10;
            int d3 = (i * 7 + 2) % 10;
            list.add(draw(String.format("%07d", i + 1), d1, d2, d3));
        }
        return list;
    }

    private Fc3dDrawEntity extremeDraw(String issue) {
        // A deliberately extreme, out-of-pattern draw (all sevens) to maximize the
        // chance of detecting any accidental use of future data.
        return draw(issue, 7, 7, 7);
    }

    private Fc3dDrawEntity draw(String issue, int d1, int d2, int d3) {
        Fc3dDrawEntity e = new Fc3dDrawEntity();
        e.setIssue(issue);
        e.setDigit1(d1);
        e.setDigit2(d2);
        e.setDigit3(d3);
        e.setSumValue(Fc3dBallUtils.sum(d1, d2, d3));
        e.setSpanValue(Fc3dBallUtils.span(d1, d2, d3));
        e.setOddEvenPattern(Fc3dBallUtils.oddEvenPattern(d1, d2, d3));
        e.setLotteryType("FC3D");
        return e;
    }
}
