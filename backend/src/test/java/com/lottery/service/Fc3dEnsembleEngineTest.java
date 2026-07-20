package com.lottery.service;

import com.lottery.config.Fc3dModelConfig;
import com.lottery.dto.Fc3dCombinationCandidate;
import com.lottery.dto.Fc3dCombinationResponse;
import com.lottery.dto.Fc3dEnsembleCandidate;
import com.lottery.dto.Fc3dEnsembleMemberInput;
import com.lottery.dto.Fc3dEnsembleResponse;
import com.lottery.dto.Fc3dFrequencyResponse;
import com.lottery.dto.Fc3dMissingResponse;
import com.lottery.dto.Fc3dSumAnalysisResponse;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.rule.fc3d.Fc3dCombinationGenerator;
import com.lottery.util.Fc3dBallUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sprint 11-A §7: verifies {@link Fc3dEnsembleEngine} — multi-model weighted-voting fusion.
 * Never asserts that any particular number is a "winning" number — only on the fusion math and
 * its structural guarantees (determinism, weight sensitivity, no invented numbers, no future
 * leakage, Top10 ⊆ Top20 ⊆ Top50).
 */
class Fc3dEnsembleEngineTest {

    private final Fc3dEnsembleEngine engine = new Fc3dEnsembleEngine();

    @Test
    void fuse_isDeterministic_forSameInputAndWeights() {
        List<Fc3dEnsembleMemberInput> members = threeMemberFixture();
        Map<String, Double> weights = defaultWeights();

        Fc3dEnsembleResponse first = engine.fuse(members, weights, 50);
        Fc3dEnsembleResponse second = engine.fuse(members, weights, 50);

        assertEquals(first.getModelVersions(), second.getModelVersions());
        assertEquals(first.getFusionMethod(), second.getFusionMethod());
        assertEquals(toSimpleView(first.getTopCandidates()), toSimpleView(second.getTopCandidates()),
                "same members + same weights must always produce the same fused ranking");
    }

    @Test
    void fuse_weightChange_changesRanking() {
        List<Fc3dEnsembleMemberInput> members = threeMemberFixture();

        Map<String, Double> favorA = Map.of("v3", 1.0, "v3-exp-001", 0.0, "baseline-freq", 0.0);
        Map<String, Double> favorC = Map.of("v3", 0.0, "v3-exp-001", 0.0, "baseline-freq", 1.0);

        Fc3dEnsembleResponse rankedFavoringA = engine.fuse(members, favorA, 50);
        Fc3dEnsembleResponse rankedFavoringC = engine.fuse(members, favorC, 50);

        String topWhenFavoringA = rankedFavoringA.getTopCandidates().get(0).getNumber();
        String topWhenFavoringC = rankedFavoringC.getTopCandidates().get(0).getNumber();

        assertEquals("123", topWhenFavoringA, "member v3's own top candidate should win when only v3 is weighted");
        assertEquals("999", topWhenFavoringC, "member baseline-freq's own top candidate should win when only it is weighted");
        assertNotEquals(topWhenFavoringA, topWhenFavoringC, "changing model weights must change the fused ranking");
    }

    @Test
    void fuse_neverProducesNumbersAbsentFromEveryInputCandidateList() {
        List<Fc3dEnsembleMemberInput> members = threeMemberFixture();
        Set<String> allowed = new HashSet<>();
        for (Fc3dEnsembleMemberInput member : members) {
            for (Fc3dCombinationCandidate candidate : member.getCandidates()) {
                allowed.add(candidate.getNumber());
            }
        }

        Fc3dEnsembleResponse fused = engine.fuse(members, defaultWeights(), 50);

        assertTrue(!fused.getTopCandidates().isEmpty(), "fixture should produce at least one fused candidate");
        for (Fc3dEnsembleCandidate candidate : fused.getTopCandidates()) {
            assertTrue(allowed.contains(candidate.getNumber()),
                    "ensemble must never invent a number that no member model proposed: " + candidate.getNumber());
        }
    }

    @Test
    void fuse_top10_isPrefixOf_top20_isPrefixOf_top50() {
        List<Fc3dEnsembleMemberInput> members = largeFixture();
        Map<String, Double> weights = defaultWeights();

        List<String> top10 = toNumbers(engine.fuse(members, weights, 10).getTopCandidates());
        List<String> top20 = toNumbers(engine.fuse(members, weights, 20).getTopCandidates());
        List<String> top50 = toNumbers(engine.fuse(members, weights, 50).getTopCandidates());

        assertEquals(10, top10.size());
        assertEquals(20, top20.size());
        assertTrue(top50.size() >= 20, "fixture must have enough distinct numbers to fill Top20");

        assertEquals(top10, top20.subList(0, 10), "Top10 must be an exact prefix of Top20");
        assertEquals(top20, top50.subList(0, 20), "Top20 must be an exact prefix of Top50");
    }

    @Test
    void fuse_neverLeaksFutureData_sameTrainPrefixYieldsSameFusedRanking() {
        Fc3dAnalyticsService analyticsService = new Fc3dAnalyticsService(null);

        List<Fc3dDrawEntity> historyA = buildHistory(40);
        List<Fc3dDrawEntity> historyB = new ArrayList<>(historyA.subList(0, 35));
        for (int i = 35; i < 60; i++) {
            historyB.add(draw(String.format(Locale.ROOT, "%07d", i + 1), 9, 9, 9));
        }
        List<Fc3dDrawEntity> trainA = historyA.subList(0, 30);
        List<Fc3dDrawEntity> trainB = historyB.subList(0, 30);

        Fc3dModelConfig productionConfig = new Fc3dModelConfig();
        Fc3dModelConfig experimentConfig = new Fc3dModelConfig();
        experimentConfig.setWeightFrequency(0.1);
        experimentConfig.setWeightSpan(0.5);
        experimentConfig.setCombinationVersion("v3-exp-001");

        Fc3dEnsembleResponse fusedFromA = fuseFromHistory(analyticsService, trainA, productionConfig, experimentConfig);
        Fc3dEnsembleResponse fusedFromB = fuseFromHistory(analyticsService, trainB, productionConfig, experimentConfig);

        assertEquals(toSimpleView(fusedFromA.getTopCandidates()), toSimpleView(fusedFromB.getTopCandidates()),
                "fused ranking for the SAME train prefix must be identical regardless of what draws come after it");
    }

    private Fc3dEnsembleResponse fuseFromHistory(Fc3dAnalyticsService analyticsService, List<Fc3dDrawEntity> train,
                                                 Fc3dModelConfig productionConfig, Fc3dModelConfig experimentConfig) {
        Fc3dFrequencyResponse frequency = analyticsService.getPositionFrequency(train);
        Fc3dMissingResponse missing = analyticsService.calculateMissing(train);
        Fc3dSumAnalysisResponse sumAnalysis = analyticsService.calculateSumAnalysis(train, 30);

        Fc3dCombinationResponse production = new Fc3dCombinationGenerator(productionConfig)
                .generate(train, frequency, missing, sumAnalysis);
        Fc3dCombinationResponse experiment = new Fc3dCombinationGenerator(experimentConfig)
                .generate(train, frequency, missing, sumAnalysis);

        List<Fc3dEnsembleMemberInput> members = List.of(
                new Fc3dEnsembleMemberInput(productionConfig.getCombinationVersion(), production.getCandidates()),
                new Fc3dEnsembleMemberInput(experimentConfig.getCombinationVersion(), experiment.getCandidates()));
        Map<String, Double> weights = Map.of(
                productionConfig.getCombinationVersion(), 0.5,
                experimentConfig.getCombinationVersion(), 0.3);
        return engine.fuse(members, weights, 50);
    }

    private Map<String, Double> defaultWeights() {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("v3", 0.5);
        weights.put("v3-exp-001", 0.3);
        weights.put("baseline-freq", 0.2);
        return weights;
    }

    private List<Fc3dEnsembleMemberInput> threeMemberFixture() {
        Fc3dEnsembleMemberInput a = new Fc3dEnsembleMemberInput("v3", List.of(
                candidate("123", 100), candidate("456", 80), candidate("789", 60)));
        Fc3dEnsembleMemberInput b = new Fc3dEnsembleMemberInput("v3-exp-001", List.of(
                candidate("123", 90), candidate("456", 40), candidate("111", 100)));
        Fc3dEnsembleMemberInput c = new Fc3dEnsembleMemberInput("baseline-freq", List.of(
                candidate("111", 80), candidate("999", 100)));
        return List.of(a, b, c);
    }

    private List<Fc3dEnsembleMemberInput> largeFixture() {
        List<Fc3dCombinationCandidate> a = new ArrayList<>();
        List<Fc3dCombinationCandidate> b = new ArrayList<>();
        List<Fc3dCombinationCandidate> c = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            a.add(candidate(String.format(Locale.ROOT, "%03d", i), 100 - i));
            b.add(candidate(String.format(Locale.ROOT, "%03d", i + 15), 100 - i));
            c.add(candidate(String.format(Locale.ROOT, "%03d", i + 40), 100 - i));
        }
        return List.of(
                new Fc3dEnsembleMemberInput("v3", a),
                new Fc3dEnsembleMemberInput("v3-exp-001", b),
                new Fc3dEnsembleMemberInput("baseline-freq", c));
    }

    private Fc3dCombinationCandidate candidate(String number, int score) {
        return new Fc3dCombinationCandidate(number, score, 0, new ArrayList<>(), "LOW");
    }

    private List<String> toNumbers(List<Fc3dEnsembleCandidate> candidates) {
        return candidates.stream().map(Fc3dEnsembleCandidate::getNumber).toList();
    }

    private List<String> toSimpleView(List<Fc3dEnsembleCandidate> candidates) {
        List<String> view = new ArrayList<>();
        for (Fc3dEnsembleCandidate candidate : candidates) {
            view.add(candidate.getNumber() + ":" + candidate.getEnsembleScore() + ":" + candidate.getVoteCount());
        }
        return view;
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
