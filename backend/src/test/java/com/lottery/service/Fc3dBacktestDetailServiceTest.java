package com.lottery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lottery.config.Fc3dModelConfig;
import com.lottery.dto.Fc3dBacktestCandidate;
import com.lottery.dto.Fc3dBacktestDetailItem;
import com.lottery.dto.Fc3dBacktestDetailResponse;
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
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Sprint 11-D: verifies {@link Fc3dBacktestDetailService} — walk-forward Top-N detail with
 * no future leakage, correct Top-N size / hit rank / hitRate / averageHitRank /
 * longestMissStreak. Never asserts on any particular winning number.
 */
@ExtendWith(MockitoExtension.class)
class Fc3dBacktestDetailServiceTest {

    @Mock
    private Fc3dPredictService fc3dPredictService;

    @Mock
    private Fc3dModelMetricService fc3dModelMetricService;

    private Fc3dModelRegistryService registryService;
    private Fc3dBacktestDetailService detailService;

    @BeforeEach
    void setUp() {
        lenient().when(fc3dModelMetricService.latestFor(any())).thenReturn(Optional.empty());

        Fc3dModelConfig baseConfig = new Fc3dModelConfig();
        registryService = new Fc3dModelRegistryService(
                baseConfig,
                new Fc3dModelRegistryMapperFake(),
                new Fc3dModelSwitchLogMapperFake(),
                fc3dModelMetricService,
                new ObjectMapper());

        Fc3dAnalyticsService analyticsService = new Fc3dAnalyticsService(null);
        detailService = new Fc3dBacktestDetailService(fc3dPredictService, analyticsService, registryService);
    }

    @Test
    void predictTopN_neverLeaksFutureData_sharedPrefixYieldsIdenticalRanking() {
        List<Fc3dDrawEntity> historyA = buildHistory(60);
        List<Fc3dDrawEntity> trainA = historyA.subList(0, 50);

        List<Fc3dDrawEntity> historyB = new ArrayList<>(historyA.subList(0, 50));
        for (int i = 50; i < 60; i++) {
            historyB.add(draw(String.format(Locale.ROOT, "%07d", i + 1), 9, 9, 9));
        }
        List<Fc3dDrawEntity> trainB = historyB.subList(0, 50);

        Fc3dCombinationGenerator generator = new Fc3dCombinationGenerator(registryService.resolveConfig(null));
        List<Fc3dBacktestCandidate> rankedA = detailService.predictTopN(generator, trainA, 50);
        List<Fc3dBacktestCandidate> rankedB = detailService.predictTopN(generator, trainB, 50);

        assertEquals(toSimpleView(rankedA), toSimpleView(rankedB),
                "identical train slices (regardless of what comes after index 50) must yield identical Top50");
    }

    @Test
    void evaluateDetails_topNSizeIsCorrect() {
        List<Fc3dDrawEntity> history = buildHistory(80);
        Fc3dBacktestDetailResponse result = detailService.evaluateDetails(history, 20, 40, 50, null);

        assertFalse(result.getDetails().isEmpty());
        for (Fc3dBacktestDetailItem item : result.getDetails()) {
            assertEquals(50, item.getPredictedTop50().size(),
                    "each period must expose exactly topN candidates, got " + item.getPredictedTop50().size());
            for (int i = 0; i < item.getPredictedTop50().size(); i++) {
                assertEquals(i + 1, item.getPredictedTop50().get(i).getRank(),
                        "ranks must be 1-based contiguous");
            }
        }
    }

    @Test
    void evaluateDetails_hitRankMatchesPositionOfActualInPredictedTopN() {
        List<Fc3dDrawEntity> history = buildHistory(80);
        Fc3dBacktestDetailResponse result = detailService.evaluateDetails(history, 20, 40, 50, null);

        for (Fc3dBacktestDetailItem item : result.getDetails()) {
            int expectedRank = -1;
            Integer expectedScore = null;
            for (Fc3dBacktestCandidate c : item.getPredictedTop50()) {
                if (item.getActualNumber().equals(c.getNumber())) {
                    expectedRank = c.getRank();
                    expectedScore = c.getScore();
                    break;
                }
            }
            if (expectedRank > 0) {
                assertTrue(item.isHit());
                assertEquals(expectedRank, item.getHitRank().intValue());
                assertEquals(expectedScore, item.getHitScore());
                assertFalse("MISS".equals(item.getHitLevel()));
            } else {
                assertFalse(item.isHit());
                assertEquals(null, item.getHitRank());
                assertEquals(null, item.getHitScore());
                assertEquals("MISS", item.getHitLevel());
            }
        }
    }

    @Test
    void evaluateDetails_hitRateEqualsHitCountOverEvaluatedPeriods() {
        List<Fc3dDrawEntity> history = buildHistory(80);
        Fc3dBacktestDetailResponse result = detailService.evaluateDetails(history, 20, 40, 50, null);

        long countedHits = result.getDetails().stream().filter(Fc3dBacktestDetailItem::isHit).count();
        assertEquals(countedHits, result.getHitCount());
        assertEquals(result.getDetails().size(), result.getEvaluatedPeriods());
        double expected = result.getEvaluatedPeriods() == 0 ? 0.0
                : Math.round((double) countedHits / result.getEvaluatedPeriods() * 10000.0) / 10000.0;
        assertEquals(expected, result.getHitRate(), 1e-9);
    }

    @Test
    void evaluateDetails_averageHitRankIsMeanOfHitRanksOnly() {
        List<Fc3dDrawEntity> history = buildHistory(80);
        Fc3dBacktestDetailResponse result = detailService.evaluateDetails(history, 20, 40, 50, null);

        List<Integer> ranks = result.getDetails().stream()
                .filter(Fc3dBacktestDetailItem::isHit)
                .map(Fc3dBacktestDetailItem::getHitRank)
                .toList();
        if (ranks.isEmpty()) {
            assertEquals(0.0, result.getAverageHitRank(), 1e-9);
        } else {
            double expected = Math.round(ranks.stream().mapToInt(Integer::intValue).average().orElse(0.0) * 10000.0) / 10000.0;
            assertEquals(expected, result.getAverageHitRank(), 1e-9);
        }
    }

    @Test
    void evaluateDetails_longestMissStreakIsMaxConsecutiveMisses() {
        List<Fc3dDrawEntity> history = buildHistory(80);
        Fc3dBacktestDetailResponse result = detailService.evaluateDetails(history, 20, 40, 50, null);

        int current = 0;
        int longest = 0;
        for (Fc3dBacktestDetailItem item : result.getDetails()) {
            if (item.isHit()) {
                current = 0;
            } else {
                current++;
                longest = Math.max(longest, current);
            }
        }
        assertEquals(longest, result.getLongestMissStreak());
    }

    private String toSimpleView(List<Fc3dBacktestCandidate> candidates) {
        StringBuilder sb = new StringBuilder();
        for (Fc3dBacktestCandidate c : candidates) {
            sb.append(c.getRank()).append(':').append(c.getNumber()).append(':').append(c.getScore()).append(',');
        }
        return sb.toString();
    }

    private List<Fc3dDrawEntity> buildHistory(int n) {
        List<Fc3dDrawEntity> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int d1 = i % 10;
            int d2 = (i * 3) % 10;
            int d3 = (i * 7 + 2) % 10;
            list.add(draw(String.format(Locale.ROOT, "%07d", i + 1), d1, d2, d3));
        }
        return list;
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
