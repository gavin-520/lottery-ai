package com.lottery.service;

import com.lottery.config.Fc3dModelConfig;
import com.lottery.dto.Fc3dExperimentResult;
import com.lottery.dto.Fc3dModelEvaluationResult;
import com.lottery.dto.Fc3dWeightOverride;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.rule.fc3d.Fc3dCombinationGenerator;
import com.lottery.util.Fc3dBallUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Sprint 10-C: verifies {@link Fc3dModelEvaluationService} — model comparison and
 * parameter-optimization experiments. Never asserts on generated/recommended numbers,
 * only on statistical evaluation metrics.
 */
@ExtendWith(MockitoExtension.class)
class Fc3dModelEvaluationServiceTest {

    @Mock
    private Fc3dPredictService fc3dPredictService;

    private Fc3dModelEvaluationService evaluationService;
    private Fc3dModelConfig config;

    @BeforeEach
    void setUp() {
        config = new Fc3dModelConfig();
        Fc3dAnalyticsService analyticsService = new Fc3dAnalyticsService(null);
        evaluationService = new Fc3dModelEvaluationService(fc3dPredictService, analyticsService, config);
    }

    @Test
    void randomBaseline_isStable_forFixedTrainSlice() {
        List<Fc3dDrawEntity> train = buildHistory(40);

        List<String> first = evaluationService.randomBaselineRanking(train);
        List<String> second = evaluationService.randomBaselineRanking(train);

        assertEquals(first, second, "random baseline must be deterministic for the same train slice");
        assertEquals(50, first.size());
    }

    @Test
    void compareModels_isConsistent_forSameHistoryAndParameters() {
        List<Fc3dDrawEntity> history = buildHistory(80);
        when(fc3dPredictService.listHistoryAsc()).thenReturn(history);

        List<Fc3dModelEvaluationResult> first = evaluationService.compareModels(20, 40);
        List<Fc3dModelEvaluationResult> second = evaluationService.compareModels(20, 40);

        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            Fc3dModelEvaluationResult a = first.get(i);
            Fc3dModelEvaluationResult b = second.get(i);
            assertEquals(a.getModelName(), b.getModelName());
            assertEquals(a.getEvaluatedPeriods(), b.getEvaluatedPeriods());
            assertEquals(a.getTop10HitRate(), b.getTop10HitRate());
            assertEquals(a.getTop20HitRate(), b.getTop20HitRate());
            assertEquals(a.getTop50HitRate(), b.getTop50HitRate());
            assertEquals(a.getImprovementVsRandom(), b.getImprovementVsRandom());
            assertEquals(a.getImprovementVsFrequency(), b.getImprovementVsFrequency());
        }
    }

    @Test
    void ranking_neverUsesFutureData_sameTrainPrefixYieldsSameRanking() {
        List<Fc3dDrawEntity> historyA = buildHistory(40);
        List<Fc3dDrawEntity> historyB = new ArrayList<>(historyA.subList(0, 35));
        for (int i = 35; i < 60; i++) {
            historyB.add(extremeDraw(String.format("%07d", i + 1)));
        }

        List<Fc3dDrawEntity> trainA = historyA.subList(0, 30);
        List<Fc3dDrawEntity> trainB = historyB.subList(0, 30);

        assertEquals(
                evaluationService.randomBaselineRanking(trainA),
                evaluationService.randomBaselineRanking(trainB),
                "random baseline ranking must not depend on data beyond the train slice");

        Fc3dCombinationGenerator currentModelGenerator = new Fc3dCombinationGenerator(config);
        assertEquals(
                evaluationService.combinationRanking(currentModelGenerator, trainA),
                evaluationService.combinationRanking(currentModelGenerator, trainB),
                "combination-model ranking must not depend on data beyond the train slice");
    }

    @Test
    void differentWeightParameters_produceDifferentRankings() {
        List<Fc3dDrawEntity> train = buildHistory(60);

        Fc3dModelConfig frequencyOnly = new Fc3dModelConfig();
        frequencyOnly.setWeightFrequency(1.0);
        frequencyOnly.setWeightMissing(0.0);
        frequencyOnly.setWeightSum(0.0);
        frequencyOnly.setWeightOddEven(0.0);
        frequencyOnly.setWeightSpan(0.0);
        frequencyOnly.setWeightAntiRepeatBonus(0.0);
        frequencyOnly.setWeightAntiRepeatPenalty(1.0);

        Fc3dModelConfig spanOnly = new Fc3dModelConfig();
        spanOnly.setWeightFrequency(0.0);
        spanOnly.setWeightMissing(0.0);
        spanOnly.setWeightSum(0.0);
        spanOnly.setWeightOddEven(0.0);
        spanOnly.setWeightSpan(1.0);
        spanOnly.setWeightAntiRepeatBonus(0.0);
        spanOnly.setWeightAntiRepeatPenalty(1.0);

        List<String> frequencyRanking = evaluationService.combinationRanking(
                new Fc3dCombinationGenerator(frequencyOnly), train);
        List<String> spanRanking = evaluationService.combinationRanking(
                new Fc3dCombinationGenerator(spanOnly), train);

        assertNotEquals(frequencyRanking, spanRanking,
                "different weight parameters must change the resulting ranking");
    }

    @Test
    void runExperiments_withDifferentParameterSets_returnsDistinctMetricsAndParameters() {
        List<Fc3dDrawEntity> history = buildHistory(80);
        when(fc3dPredictService.listHistoryAsc()).thenReturn(history);

        Fc3dWeightOverride a = new Fc3dWeightOverride(1.0, 0.0, 0.0, 0.0, 0.0);
        Fc3dWeightOverride b = new Fc3dWeightOverride(0.0, 0.0, 0.0, 0.0, 1.0);

        List<Fc3dExperimentResult> results = evaluationService.runExperiments(List.of(a, b), 20, 40);

        assertEquals(2, results.size());
        assertEquals("experiment-001", results.get(0).getExperimentId());
        assertEquals("experiment-002", results.get(1).getExperimentId());
        assertNotEquals(results.get(0).getParameters(), results.get(1).getParameters());
        assertTrue(results.get(0).getMetrics().getEvaluatedPeriods() > 0);
        assertTrue(results.get(1).getMetrics().getEvaluatedPeriods() > 0);
    }

    @Test
    void compareModels_hitRates_areNonDecreasing_top10ToTop20ToTop50() {
        List<Fc3dDrawEntity> history = buildHistory(80);
        when(fc3dPredictService.listHistoryAsc()).thenReturn(history);

        List<Fc3dModelEvaluationResult> results = evaluationService.compareModels(20, 40);

        for (Fc3dModelEvaluationResult result : results) {
            assertRateInRange(result.getTop10HitRate());
            assertRateInRange(result.getTop20HitRate());
            assertRateInRange(result.getTop50HitRate());
            assertTrue(result.getTop10HitRate() <= result.getTop20HitRate() + 1e-9,
                    result.getModelName() + ": top10 must be <= top20");
            assertTrue(result.getTop20HitRate() <= result.getTop50HitRate() + 1e-9,
                    result.getModelName() + ": top20 must be <= top50");
        }
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
