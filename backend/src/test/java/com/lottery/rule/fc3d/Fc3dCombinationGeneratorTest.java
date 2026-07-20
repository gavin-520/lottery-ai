package com.lottery.rule.fc3d;

import com.lottery.config.Fc3dModelConfig;
import com.lottery.dto.Fc3dCombinationCandidate;
import com.lottery.dto.Fc3dCombinationResponse;
import com.lottery.dto.Fc3dFrequencyResponse;
import com.lottery.dto.Fc3dMissingResponse;
import com.lottery.dto.Fc3dSumAnalysisResponse;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.service.Fc3dAnalyticsService;
import com.lottery.util.Fc3dBallUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sprint 10-B: verifies the Top50 combination-pool generator.
 * Fc3dRuleEngine (Sprint 9-B/10-A) is untouched and covered separately by Fc3dRuleEngineTest.
 */
class Fc3dCombinationGeneratorTest {

    private Fc3dCombinationGenerator generator;
    private Fc3dAnalyticsService analyticsService;
    private List<Fc3dDrawEntity> fixedHistory;

    @BeforeEach
    void setUp() {
        generator = new Fc3dCombinationGenerator(new Fc3dModelConfig());
        analyticsService = new Fc3dAnalyticsService(null);
        fixedHistory = buildFixedHistory();
    }

    @Test
    void generate_isStable_forFixedHistoryInput() {
        Fc3dCombinationResponse first = generate();
        Fc3dCombinationResponse second = generate();

        assertEquals(first.getTotalCandidates(), second.getTotalCandidates());
        assertEquals(first.getModelVersion(), second.getModelVersion());
        assertEquals(first.getCandidates().size(), second.getCandidates().size());
        for (int i = 0; i < first.getCandidates().size(); i++) {
            Fc3dCombinationCandidate a = first.getCandidates().get(i);
            Fc3dCombinationCandidate b = second.getCandidates().get(i);
            assertEquals(a.getNumber(), b.getNumber());
            assertEquals(a.getScore(), b.getScore());
            assertEquals(a.getRank(), b.getRank());
            assertEquals(a.getRiskLevel(), b.getRiskLevel());
            assertEquals(a.getReasons(), b.getReasons());
        }
    }

    @Test
    void generate_returnsExactlyConfiguredCandidateCount() {
        Fc3dCombinationResponse response = generate();

        assertEquals(50, response.getTotalCandidates());
        assertEquals(50, response.getCandidates().size());
    }

    @Test
    void generate_candidatesAreSortedByScoreDescending() {
        List<Fc3dCombinationCandidate> candidates = generate().getCandidates();

        for (int i = 1; i < candidates.size(); i++) {
            assertTrue(candidates.get(i - 1).getScore() >= candidates.get(i).getScore(),
                    "candidates must be sorted by score descending at index " + i);
            assertEquals(i, candidates.get(i - 1).getRank(), "rank must be 1-based and sequential");
        }
    }

    @Test
    void generate_hasNoDuplicateNumbers() {
        List<Fc3dCombinationCandidate> candidates = generate().getCandidates();
        Set<String> numbers = new HashSet<>();
        for (Fc3dCombinationCandidate c : candidates) {
            assertTrue(numbers.add(c.getNumber()), "duplicate candidate number: " + c.getNumber());
        }
        assertEquals(candidates.size(), numbers.size());
    }

    @Test
    void generate_numbersAreWithin000To999Range() {
        List<Fc3dCombinationCandidate> candidates = generate().getCandidates();
        for (Fc3dCombinationCandidate c : candidates) {
            String number = c.getNumber();
            assertEquals(3, number.length(), "number must be exactly 3 digits: " + number);
            for (char ch : number.toCharArray()) {
                assertTrue(ch >= '0' && ch <= '9', "number must only contain digits 0-9: " + number);
            }
            int value = Integer.parseInt(number);
            assertTrue(value >= 0 && value <= 999, "number must be within 000-999: " + number);
        }
    }

    private Fc3dCombinationResponse generate() {
        Fc3dFrequencyResponse frequency = analyticsService.getPositionFrequency(fixedHistory);
        Fc3dMissingResponse missing = analyticsService.calculateMissing(fixedHistory);
        Fc3dSumAnalysisResponse sum = analyticsService.calculateSumAnalysis(fixedHistory, 20);
        return generator.generate(fixedHistory, frequency, missing, sum);
    }

    /**
     * Deterministic ascending history (80 periods) with cycling digits.
     */
    private List<Fc3dDrawEntity> buildFixedHistory() {
        List<Fc3dDrawEntity> list = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
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
