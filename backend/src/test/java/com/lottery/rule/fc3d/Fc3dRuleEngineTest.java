package com.lottery.rule.fc3d;

import com.lottery.config.Fc3dModelConfig;
import com.lottery.dto.Fc3dCandidate;
import com.lottery.dto.Fc3dFrequencyResponse;
import com.lottery.dto.Fc3dMissingResponse;
import com.lottery.dto.Fc3dSumAnalysisResponse;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.service.Fc3dAnalyticsService;
import com.lottery.util.Fc3dBallUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Fc3dRuleEngineTest {

    private Fc3dModelConfig defaultConfig;
    private Fc3dRuleEngine ruleEngine;
    private Fc3dAnalyticsService analyticsService;
    private List<Fc3dDrawEntity> fixedHistory;

    @BeforeEach
    void setUp() {
        defaultConfig = new Fc3dModelConfig();
        ruleEngine = new Fc3dRuleEngine(defaultConfig);
        // Analytics list-based methods do not need PredictService when history is passed in.
        analyticsService = new Fc3dAnalyticsService(null);
        fixedHistory = buildFixedHistory();
    }

    @Test
    void generateCandidates_moreThanOne() {
        List<Fc3dCandidate> candidates = generate();
        assertTrue(candidates.size() > 1, "expected multiple statistical candidates");
    }

    @Test
    void generateCandidates_scoreInRange() {
        for (Fc3dCandidate c : generate()) {
            assertTrue(c.getScore() >= 0 && c.getScore() <= 100,
                    "score out of range: " + c.getScore());
        }
    }

    @Test
    void generateCandidates_reasonsNonEmpty() {
        for (Fc3dCandidate c : generate()) {
            assertFalse(c.getReasons() == null || c.getReasons().isEmpty(),
                    "reasons should not be empty for " + c.getNumber());
        }
    }

    @Test
    void generateCandidates_stableForFixedHistory() {
        List<Fc3dCandidate> first = generate();
        List<Fc3dCandidate> second = generate();
        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).getNumber(), second.get(i).getNumber());
            assertEquals(first.get(i).getScore(), second.get(i).getScore());
            assertEquals(first.get(i).getReasons(), second.get(i).getReasons());
        }
        // Anchor: best candidate remains deterministic for this fixture
        assertEquals(first.get(0).getNumber(), second.get(0).getNumber());
        assertFalse(first.get(0).getNumber().isBlank());
    }

    @Test
    void defaultConfig_matchesLegacyHardcodedWeights() {
        // Sprint 10-A externalized weights to Fc3dModelConfig; these literals are the
        // PREVIOUSLY hardcoded constants. Default config must produce identical output.
        Fc3dModelConfig legacyLiteral = new Fc3dModelConfig();
        legacyLiteral.setWeightFrequency(0.35);
        legacyLiteral.setWeightMissing(0.20);
        legacyLiteral.setWeightSum(0.25);
        legacyLiteral.setWeightOddEven(0.15);
        legacyLiteral.setWeightAntiRepeatBonus(0.05);
        legacyLiteral.setWeightAntiRepeatPenalty(0.35);
        legacyLiteral.setPoolPerPosition(4);
        legacyLiteral.setRecentAvoidPeriods(5);
        Fc3dRuleEngine legacyEngine = new Fc3dRuleEngine(legacyLiteral);

        List<Fc3dCandidate> defaultOutput = generate();
        List<Fc3dCandidate> legacyOutput = generateWith(legacyEngine);

        assertEquals(legacyOutput, defaultOutput,
                "default Fc3dModelConfig must reproduce the pre-Sprint-10-A hardcoded behavior");
    }

    @Test
    void customWeights_changeCandidateRanking() {
        Fc3dModelConfig heavySum = new Fc3dModelConfig();
        heavySum.setWeightFrequency(0.05);
        heavySum.setWeightMissing(0.05);
        heavySum.setWeightSum(0.80);
        heavySum.setWeightOddEven(0.05);
        Fc3dRuleEngine heavySumEngine = new Fc3dRuleEngine(heavySum);

        List<Fc3dCandidate> defaultOutput = generate();
        List<Fc3dCandidate> heavySumOutput = generateWith(heavySumEngine);

        assertNotEquals(defaultOutput, heavySumOutput,
                "changing configured weights must change the resulting candidate scores/ranking");
    }

    private List<Fc3dCandidate> generate() {
        return generateWith(ruleEngine);
    }

    private List<Fc3dCandidate> generateWith(Fc3dRuleEngine engine) {
        Fc3dFrequencyResponse frequency = analyticsService.getPositionFrequency(fixedHistory);
        Fc3dMissingResponse missing = analyticsService.calculateMissing(fixedHistory);
        Fc3dSumAnalysisResponse sum = analyticsService.calculateSumAnalysis(fixedHistory, 20);
        return engine.generateCandidates(fixedHistory, frequency, missing, sum);
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
