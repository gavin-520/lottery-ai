package com.lottery.service;

import com.lottery.config.Fc3dModelConfig;
import com.lottery.dto.Fc3dAnalyzeResponse;
import com.lottery.dto.Fc3dCandidate;
import com.lottery.dto.Fc3dFrequencyResponse;
import com.lottery.dto.Fc3dMissingResponse;
import com.lottery.dto.Fc3dOddEvenResponse;
import com.lottery.dto.Fc3dPredictResponse;
import com.lottery.dto.Fc3dSumAnalysisResponse;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.rule.fc3d.Fc3dCombinationGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Fc3dAnalyzeServiceTest {

    private static final String[] FORBIDDEN_PHRASES = {"保证中奖", "预测中奖号码", "必中", "包中"};

    @Mock
    private Fc3dAnalyticsService fc3dAnalyticsService;

    @Mock
    private Fc3dPredictService fc3dPredictService;

    @Mock
    private AiServiceClient aiServiceClient;

    private Fc3dAnalyzeService analyzeService;

    private List<Fc3dDrawEntity> history;
    private Fc3dPredictResponse predicted;

    @BeforeEach
    void setUp() {
        Fc3dCombinationGenerator combinationGenerator = new Fc3dCombinationGenerator(new Fc3dModelConfig());
        analyzeService = new Fc3dAnalyzeService(fc3dAnalyticsService, fc3dPredictService, aiServiceClient, combinationGenerator);

        history = buildHistory(50);
        when(fc3dPredictService.listHistoryAsc()).thenReturn(history);

        Fc3dFrequencyResponse frequency = new Fc3dFrequencyResponse();
        frequency.setTotalPeriods(history.size());
        frequency.setHundreds(freqMap(new int[]{5, 30, 4, 4, 4, 4, 4, 4, 4, 4}));
        frequency.setTens(freqMap(new int[]{4, 4, 20, 4, 4, 4, 4, 4, 4, 4}));
        frequency.setUnits(freqMap(new int[]{4, 4, 4, 4, 4, 25, 4, 4, 4, 4}));
        lenient().when(fc3dAnalyticsService.getPositionFrequency(history)).thenReturn(frequency);

        Fc3dMissingResponse missing = new Fc3dMissingResponse();
        missing.setTotalPeriods(history.size());
        missing.setItems(List.of());
        lenient().when(fc3dAnalyticsService.calculateMissing(history)).thenReturn(missing);

        Fc3dSumAnalysisResponse sumAnalysis = new Fc3dSumAnalysisResponse();
        sumAnalysis.setTotalPeriods(history.size());
        sumAnalysis.setAverage(13.6);
        sumAnalysis.setDistribution(Map.of("13", 40, "14", 35));
        sumAnalysis.setRecentTrend(List.of());
        lenient().when(fc3dAnalyticsService.calculateSumAnalysis(history, 30)).thenReturn(sumAnalysis);

        Fc3dOddEvenResponse oddEven = new Fc3dOddEvenResponse();
        oddEven.setPattern("OOE");
        oddEven.setOddCount(2);
        oddEven.setEvenCount(1);
        lenient().when(fc3dAnalyticsService.calculateOddEvenAnalysis(history)).thenReturn(oddEven);

        List<Fc3dCandidate> candidates = new ArrayList<>();
        candidates.add(new Fc3dCandidate("123", 85,
                List.of("百位热号", "和值处于历史高频区间", "奇偶结构接近历史常见形态")));
        candidates.add(new Fc3dCandidate("456", 40, List.of("近几期已出现，降权处理")));
        candidates.add(new Fc3dCandidate("111", 20, List.of("综合频率与形态的统计候选")));

        predicted = new Fc3dPredictResponse();
        predicted.setLotteryType("FC3D");
        predicted.setIssue("next");
        predicted.setCandidates(candidates);
        predicted.setBest("123");
        lenient().when(fc3dPredictService.predictByRules(anyString())).thenReturn(predicted);
    }

    @Test
    void analyze_usesAiResponse_whenAiServiceAvailable() {
        Fc3dAnalyzeResponse aiResponse = new Fc3dAnalyzeResponse();
        aiResponse.setModelName("fc3d-analyze-v1");
        when(aiServiceClient.analyzeFc3d(any(), any(), any(), any(), anyList(), anyString(), anyList(), anyString(), any()))
                .thenReturn(aiResponse);

        Fc3dAnalyzeResponse result = analyzeService.analyze("next", "请解释统计候选");

        assertEquals("fc3d-analyze-v1", result.getModelName());
        verify(aiServiceClient).analyzeFc3d(any(), any(), any(), any(), anyList(), eq("123"), anyList(), eq("next"), eq("请解释统计候选"));
    }

    @Test
    void analyze_fallsBackToLocalExplanation_whenAiUnavailable() {
        when(aiServiceClient.analyzeFc3d(any(), any(), any(), any(), anyList(), anyString(), anyList(), anyString(), any()))
                .thenReturn(null);

        Fc3dAnalyzeResponse result = analyzeService.analyze("next", "请解释统计候选");

        assertEquals("fc3d-analyze-fallback-v1", result.getModelName());
        assertEquals(3, result.getCandidateAnalysis().size());
        assertEquals("123", result.getRecommendation().getPreferred());
        assertEquals(Fc3dAnalyzeService.DISCLAIMER, result.getRecommendation().getDisclaimer());
        assertTrue(result.getConfidence() >= 0.4 && result.getConfidence() <= 0.85);
    }

    @Test
    void fallback_flagsRepeatedCandidateAsRisk() {
        when(aiServiceClient.analyzeFc3d(any(), any(), any(), any(), anyList(), anyString(), anyList(), anyString(), any()))
                .thenReturn(null);

        Fc3dAnalyzeResponse result = analyzeService.analyze("next", null);
        var repeated = result.getCandidateAnalysis().stream()
                .filter(c -> c.getNumber().equals("456"))
                .findFirst()
                .orElseThrow();

        assertFalse(repeated.getRiskFlags().isEmpty());
    }

    @Test
    void fallback_neverGeneratesNewNumbers_onlyExplainsExistingCandidates() {
        when(aiServiceClient.analyzeFc3d(any(), any(), any(), any(), anyList(), anyString(), anyList(), anyString(), any()))
                .thenReturn(null);

        Fc3dAnalyzeResponse result = analyzeService.analyze("next", null);
        List<String> analyzedNumbers = result.getCandidateAnalysis().stream().map(c -> c.getNumber()).toList();
        List<String> originalNumbers = predicted.getCandidates().stream().map(Fc3dCandidate::getNumber).toList();

        assertEquals(originalNumbers, analyzedNumbers);
    }

    @Test
    void fallback_outputContainsNoForbiddenWinGuaranteeLanguage() {
        when(aiServiceClient.analyzeFc3d(any(), any(), any(), any(), anyList(), anyString(), anyList(), anyString(), any()))
                .thenReturn(null);

        Fc3dAnalyzeResponse result = analyzeService.analyze("next", "会中奖吗？");
        String serialized = result.toString();
        for (String phrase : FORBIDDEN_PHRASES) {
            assertFalse(serialized.contains(phrase), "found forbidden phrase: " + phrase);
        }
    }

    @Test
    void analyze_candidateCoverage_isAttached_onAiSuccessAndFallbackPaths() {
        Fc3dAnalyzeResponse aiResponse = new Fc3dAnalyzeResponse();
        aiResponse.setModelName("fc3d-analyze-v1");
        when(aiServiceClient.analyzeFc3d(any(), any(), any(), any(), anyList(), anyString(), anyList(), anyString(), any()))
                .thenReturn(aiResponse);

        Fc3dAnalyzeResponse aiResult = analyzeService.analyze("next", null);
        assertNotNull(aiResult.getCandidateCoverage(), "AI-success path must carry candidateCoverage (Sprint 10-B)");
        assertTrue(aiResult.getCandidateCoverage().getTotal() > 0);
        assertNotNull(aiResult.getCandidateCoverage().getScoreRange());
        assertFalse(aiResult.getCandidateCoverage().getRiskDistribution().isEmpty());

        when(aiServiceClient.analyzeFc3d(any(), any(), any(), any(), anyList(), anyString(), anyList(), anyString(), any()))
                .thenReturn(null);
        Fc3dAnalyzeResponse fallbackResult = analyzeService.analyze("next", null);
        assertNotNull(fallbackResult.getCandidateCoverage(), "fallback path must also carry candidateCoverage (Sprint 10-B)");
        assertTrue(fallbackResult.getCandidateCoverage().getTotal() > 0);
    }

    @Test
    void fallback_featuresSummarizeFrequencySumAndOddEven() {
        when(aiServiceClient.analyzeFc3d(any(), any(), any(), any(), anyList(), anyString(), anyList(), anyString(), any()))
                .thenReturn(null);

        Fc3dAnalyzeResponse result = analyzeService.analyze("next", null);
        assertNotNull(result.getFeatures());
        assertEquals(13.6, result.getFeatures().getSumAverage());
        assertEquals("OOE", result.getFeatures().getDominantOddEven());
        assertEquals(1, result.getFeatures().getHotDigits().get("hundreds").get(0));
    }

    private Map<String, Integer> freqMap(int[] counts) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < counts.length; i++) {
            map.put(String.valueOf(i), counts[i]);
        }
        return map;
    }

    private List<Fc3dDrawEntity> buildHistory(int n) {
        List<Fc3dDrawEntity> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Fc3dDrawEntity e = new Fc3dDrawEntity();
            e.setIssue(String.format("%07d", i + 1));
            e.setDigit1(i % 10);
            e.setDigit2((i + 1) % 10);
            e.setDigit3((i + 2) % 10);
            e.setLotteryType("FC3D");
            list.add(e);
        }
        return list;
    }
}
