package com.lottery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lottery.config.Fc3dModelConfig;
import com.lottery.dto.Fc3dPredictResponse;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.mapper.Fc3dDrawMapper;
import com.lottery.mapper.Fc3dModelMetricMapper;
import com.lottery.mapper.PredictRecordMapper;
import com.lottery.rule.fc3d.Fc3dRuleEngine;
import com.lottery.util.Fc3dBallUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 10-A: verifies Fc3dModelConfig is correctly threaded through to
 * Fc3dPredictResponse.modelVersion / modelName, without altering candidate
 * generation logic itself (covered separately by Fc3dRuleEngineTest).
 */
@ExtendWith(MockitoExtension.class)
class Fc3dPredictServiceTest {

    @Mock
    private Fc3dDrawMapper fc3dDrawMapper;

    @Mock
    private PredictRecordMapper predictRecordMapper;

    @Mock
    private AiServiceClient aiServiceClient;

    @Test
    void predictByRules_defaultConfig_returnsModelVersionAndLegacyModelName() {
        Fc3dModelConfig config = new Fc3dModelConfig();
        Fc3dPredictService service = buildService(config);
        when(fc3dDrawMapper.selectList(any())).thenReturn(buildHistory(60));

        Fc3dPredictResponse response = service.predictByRules("next");

        assertEquals("v2", response.getModelVersion());
        assertEquals("fc3d-statistical-model-v2", response.getModelName());
        assertNotNull(response.getBest());
        assertNotNull(response.getCandidates());
        assertEquals("rules", response.getSource());
    }

    @Test
    void predictByRules_customVersion_isReflectedInResponse() {
        Fc3dModelConfig config = new Fc3dModelConfig();
        config.setName("fc3d-statistical-model");
        config.setVersion("v3-experimental");
        Fc3dPredictService service = buildService(config);
        when(fc3dDrawMapper.selectList(any())).thenReturn(buildHistory(60));

        Fc3dPredictResponse response = service.predictByRules("next");

        assertEquals("v3-experimental", response.getModelVersion());
        assertEquals("fc3d-statistical-model-v3-experimental", response.getModelName());
    }

    private Fc3dPredictService buildService(Fc3dModelConfig config) {
        Fc3dRuleEngine ruleEngine = new Fc3dRuleEngine(config);
        Fc3dAnalyticsService analyticsService = new Fc3dAnalyticsService(null);
        Fc3dModelMetricService metricService = new Fc3dModelMetricService(mock(Fc3dModelMetricMapper.class), new ObjectMapper());
        Fc3dModelRegistryService registryService = new Fc3dModelRegistryService(config, new Fc3dModelRegistryMapperFake(),
                new Fc3dModelSwitchLogMapperFake(), metricService, new ObjectMapper());
        return new Fc3dPredictService(
                fc3dDrawMapper, ruleEngine, predictRecordMapper, aiServiceClient, analyticsService, config,
                registryService);
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
