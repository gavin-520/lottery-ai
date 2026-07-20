package com.lottery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lottery.config.Fc3dModelConfig;
import com.lottery.dto.Fc3dCombinationResponse;
import com.lottery.dto.Fc3dModelSelectionResult;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.entity.Fc3dModelMetricEntity;
import com.lottery.mapper.Fc3dDrawMapper;
import com.lottery.mapper.Fc3dModelMetricMapper;
import com.lottery.mapper.PredictRecordMapper;
import com.lottery.rule.fc3d.Fc3dRuleEngine;
import com.lottery.util.Fc3dBallUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Sprint 10-D §7: verifies {@code Fc3dModelSelector} — automatic best-model recommendation
 * over persisted walk-forward metrics, plus the Predict-chain "active model" wiring. Never
 * asserts on generated/recommended numbers, only on model-selection / metric outcomes.
 */
@ExtendWith(MockitoExtension.class)
class Fc3dModelSelectorTest {

    @Mock
    private Fc3dModelMetricMapper fc3dModelMetricMapper;

    @Mock
    private Fc3dDrawMapper fc3dDrawMapper;

    @Mock
    private PredictRecordMapper predictRecordMapper;

    @Mock
    private AiServiceClient aiServiceClient;

    private Fc3dModelConfig baseConfig;
    private Fc3dModelMetricService metricService;
    private Fc3dModelRegistryService registryService;
    private Fc3dModelSelector selector;

    @BeforeEach
    void setUp() {
        baseConfig = new Fc3dModelConfig();
        metricService = new Fc3dModelMetricService(fc3dModelMetricMapper, new ObjectMapper());
        registryService = new Fc3dModelRegistryService(baseConfig, new Fc3dModelRegistryMapperFake(),
                new Fc3dModelSwitchLogMapperFake(), metricService, new ObjectMapper());
        selector = new Fc3dModelSelector(metricService, registryService);
    }

    @Test
    void selectBest_picksModelWithHighestTop50HitRate() {
        registryService.register("v-a", weights(0.35, 0.20, 0.25, 0.15, 0.10));
        registryService.register("v-b", weights(0.10, 0.10, 0.10, 0.10, 0.60));

        LocalDateTime now = LocalDateTime.now();
        when(fc3dModelMetricMapper.selectList(any())).thenReturn(List.of(
                metric("v-a", 30, 0.10, 0.20, 0.30, 0.10, 0.05, now),
                metric("v-b", 30, 0.20, 0.35, 0.50, 0.40, 0.30, now)
        ));

        Fc3dModelSelectionResult result = selector.selectBest(10);

        assertEquals("v-b", result.getSelectedModel());
        assertTrue(result.getReason().contains("Top50覆盖率最高"));
    }

    @Test
    void selectBest_isStable_whenMetricsAreIdentical() {
        registryService.register("v-x", weights(0.35, 0.20, 0.25, 0.15, 0.10));
        registryService.register("v-y", weights(0.20, 0.20, 0.20, 0.20, 0.20));

        LocalDateTime now = LocalDateTime.now();
        when(fc3dModelMetricMapper.selectList(any())).thenReturn(List.of(
                metric("v-y", 30, 0.10, 0.20, 0.30, 0.10, 0.05, now),
                metric("v-x", 30, 0.10, 0.20, 0.30, 0.10, 0.05, now)
        ));

        Fc3dModelSelectionResult first = selector.selectBest(10);
        Fc3dModelSelectionResult second = selector.selectBest(10);
        Fc3dModelSelectionResult third = selector.selectBest(10);

        assertEquals(first.getSelectedModel(), second.getSelectedModel());
        assertEquals(second.getSelectedModel(), third.getSelectedModel());
        assertEquals("v-x", first.getSelectedModel(), "tie-break must be deterministic (version ascending)");
    }

    @Test
    void selectBest_neverConsidersMetricsRecordedAfterAsOfCutoff() {
        registryService.register("v-old", weights(0.35, 0.20, 0.25, 0.15, 0.10));
        registryService.register("v-future", weights(0.10, 0.10, 0.10, 0.10, 0.60));

        LocalDateTime cutoff = LocalDateTime.now();
        Fc3dModelMetricEntity oldRecord = metric("v-old", 30, 0.10, 0.20, 0.30, 0.10, 0.05, cutoff.minusDays(10));
        Fc3dModelMetricEntity futureRecord = metric("v-future", 30, 0.50, 0.70, 0.95, 0.90, 0.80, cutoff.plusDays(10));
        when(fc3dModelMetricMapper.selectList(any())).thenReturn(List.of(futureRecord, oldRecord));

        Fc3dModelSelectionResult result = selector.selectBest(10, cutoff);

        assertEquals("v-old", result.getSelectedModel(),
                "a metric recorded after the asOf cutoff must never influence a past selection decision");
    }

    @Test
    void selectBest_neverSelectsAnInactiveModel_evenWithBetterMetrics() {
        registryService.register("v-active", weights(0.35, 0.20, 0.25, 0.15, 0.10));
        registryService.register("v-disabled", weights(0.10, 0.10, 0.10, 0.10, 0.60));
        registryService.deactivate("v-disabled");

        LocalDateTime now = LocalDateTime.now();
        when(fc3dModelMetricMapper.selectList(any())).thenReturn(List.of(
                metric("v-active", 30, 0.10, 0.20, 0.30, 0.10, 0.05, now),
                metric("v-disabled", 30, 0.50, 0.70, 0.95, 0.90, 0.80, now)
        ));

        Fc3dModelSelectionResult result = selector.selectBest(10);

        assertEquals("v-active", result.getSelectedModel(),
                "an INACTIVE model must never be selected regardless of how strong its metrics are");
    }

    @Test
    void selectBest_returnsNull_whenNoEligibleCandidateHasMetrics() {
        // Only the bootstrap base model is registered; no metric rows recorded for it.
        when(fc3dModelMetricMapper.selectList(any())).thenReturn(List.of());

        Fc3dModelSelectionResult result = selector.selectBest(10);

        assertNull(result.getSelectedModel());
        assertTrue(result.getReason().size() > 0);
    }

    @Test
    void predictChain_generateCombinations_usesTheCurrentlyActiveRegisteredModel() {
        Map<String, Double> distinctWeights = weights(0.05, 0.05, 0.05, 0.05, 0.80);
        registryService.register("v-promoted", distinctWeights);
        registryService.activate("v-promoted");

        Fc3dRuleEngine ruleEngine = new Fc3dRuleEngine(baseConfig);
        Fc3dAnalyticsService analyticsService = new Fc3dAnalyticsService(null);
        Fc3dPredictService predictService = new Fc3dPredictService(
                fc3dDrawMapper, ruleEngine, predictRecordMapper, aiServiceClient, analyticsService,
                baseConfig, registryService);
        when(fc3dDrawMapper.selectList(any())).thenReturn(buildHistory(60));

        Fc3dCombinationResponse response = predictService.generateCombinations();

        assertEquals("v-promoted", response.getModelVersion(),
                "Predict must resolve combinations using the registry's currently ACTIVE model");
    }

    private Map<String, Double> weights(double frequency, double missing, double sum, double oddEven, double span) {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("frequency", frequency);
        map.put("missing", missing);
        map.put("sum", sum);
        map.put("oddEven", oddEven);
        map.put("span", span);
        return map;
    }

    private Fc3dModelMetricEntity metric(String version, int periods, double top10, double top20, double top50,
                                         double improvementVsRandom, double improvementVsFrequency,
                                         LocalDateTime createdTime) {
        Fc3dModelMetricEntity entity = new Fc3dModelMetricEntity();
        entity.setModelVersion(version);
        entity.setModelName(version);
        entity.setEvaluatePeriods(periods);
        entity.setTop10HitRate(top10);
        entity.setTop20HitRate(top20);
        entity.setTop50HitRate(top50);
        entity.setImprovementVsRandom(improvementVsRandom);
        entity.setImprovementVsFrequency(improvementVsFrequency);
        entity.setCreatedTime(createdTime);
        return entity;
    }

    private List<Fc3dDrawEntity> buildHistory(int n) {
        List<Fc3dDrawEntity> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int d1 = i % 10;
            int d2 = (i * 3) % 10;
            int d3 = (i * 7 + 2) % 10;
            Fc3dDrawEntity e = new Fc3dDrawEntity();
            e.setIssue(String.format("%07d", i + 1));
            e.setDigit1(d1);
            e.setDigit2(d2);
            e.setDigit3(d3);
            e.setSumValue(Fc3dBallUtils.sum(d1, d2, d3));
            e.setSpanValue(Fc3dBallUtils.span(d1, d2, d3));
            e.setOddEvenPattern(Fc3dBallUtils.oddEvenPattern(d1, d2, d3));
            e.setLotteryType("FC3D");
            list.add(e);
        }
        return list;
    }
}
