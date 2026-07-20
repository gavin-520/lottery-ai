package com.lottery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lottery.config.Fc3dModelConfig;
import com.lottery.dto.Fc3dEnsembleCandidate;
import com.lottery.dto.Fc3dEnsembleMemberInput;
import com.lottery.dto.Fc3dEnsembleOptimizationResponse;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.util.Fc3dBallUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Sprint 11-B §5: verifies {@link Fc3dEnsembleWeightOptimizer} — grid-search auto-optimization
 * of Ensemble fusion weights. Never asserts on any particular "winning" number, only on the
 * optimization contract itself (weight-sum constraint, determinism, no invented candidates, no
 * future leakage, optimized result never worse than the current baseline).
 */
@ExtendWith(MockitoExtension.class)
class Fc3dEnsembleWeightOptimizerTest {

    @Mock
    private Fc3dPredictService fc3dPredictService;

    @Mock
    private Fc3dModelMetricService fc3dModelMetricService;

    private Fc3dModelRegistryService registryService;
    private Fc3dEnsembleWeightOptimizer optimizer;
    private List<String> versions;

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
        // bootstrap already registered baseConfig.getCombinationVersion() ("v3") as ACTIVE + production.
        registryService.register("v3-exp-001", weights(0.10, 0.10, 0.10, 0.10, 0.60));
        registryService.register("frequency-only-baseline", weights(1.0, 0.0, 0.0, 0.0, 0.0));

        Fc3dAnalyticsService analyticsService = new Fc3dAnalyticsService(null);
        Fc3dEnsembleEngine engine = new Fc3dEnsembleEngine();
        Fc3dEnsembleWeightOverrideService overrideService = new Fc3dEnsembleWeightOverrideService();
        Fc3dEnsemblePredictService ensemblePredictService =
                new Fc3dEnsemblePredictService(fc3dPredictService, analyticsService, registryService, engine, overrideService);
        optimizer = new Fc3dEnsembleWeightOptimizer(
                fc3dPredictService, analyticsService, registryService, ensemblePredictService, engine);

        versions = List.of("v3", "v3-exp-001", "frequency-only-baseline");
    }

    @Test
    void optimize_bestWeights_alwaysSumToOne() {
        when(fc3dPredictService.listHistoryAsc()).thenReturn(buildHistory(70));

        Fc3dEnsembleOptimizationResponse result = optimizer.optimize(versions, 20, 40);

        double sum = result.getBestWeights().values().stream().mapToDouble(Double::doubleValue).sum();
        assertTrue(Math.abs(sum - 1.0) < 1e-6, "bestWeights must sum to 1, got " + sum);

        double currentSum = result.getCurrentWeights().values().stream().mapToDouble(Double::doubleValue).sum();
        assertTrue(Math.abs(currentSum - 1.0) < 1e-6, "currentWeights must also sum to 1, got " + currentSum);
    }

    @Test
    void optimize_isDeterministic_forSameHistory() {
        when(fc3dPredictService.listHistoryAsc()).thenReturn(buildHistory(70));

        Fc3dEnsembleOptimizationResponse first = optimizer.optimize(versions, 20, 40);
        Fc3dEnsembleOptimizationResponse second = optimizer.optimize(versions, 20, 40);

        assertEquals(first.getBestWeights(), second.getBestWeights(),
                "same history + same models must always recommend the same weights");
        assertEquals(first.getAfter().getTop50(), second.getAfter().getTop50());
        assertEquals(first.getAfter().getTop20(), second.getAfter().getTop20());
        assertEquals(first.getAfter().getTop10(), second.getAfter().getTop10());
    }

    @Test
    void buildSnapshots_neverProducesNumbersAbsentFromEveryMemberCandidateList() {
        when(fc3dPredictService.listHistoryAsc()).thenReturn(buildHistory(70));

        List<Fc3dEnsembleWeightOptimizer.PeriodSnapshot> snapshots = optimizer.buildSnapshots(versions, 20, 30);
        assertTrue(!snapshots.isEmpty(), "fixture history must yield at least one walk-forward period");

        Fc3dEnsembleEngine engine = new Fc3dEnsembleEngine();
        Map<String, Double> weights = Map.of("v3", 0.5, "v3-exp-001", 0.3, "frequency-only-baseline", 0.2);

        for (Fc3dEnsembleWeightOptimizer.PeriodSnapshot snapshot : snapshots) {
            Set<String> allowed = new HashSet<>();
            for (Fc3dEnsembleMemberInput member : snapshot.members) {
                member.getCandidates().forEach(c -> allowed.add(c.getNumber()));
            }
            for (Fc3dEnsembleCandidate fused : engine.fuseAll(snapshot.members, weights)) {
                assertTrue(allowed.contains(fused.getNumber()),
                        "optimizer must never evaluate a number no member model proposed: " + fused.getNumber());
            }
        }
    }

    @Test
    void buildSnapshots_neverLeaksFutureData_sharedPrefixYieldsIdenticalEarlierSnapshots() {
        List<Fc3dDrawEntity> historyA = buildHistory(40);
        List<Fc3dDrawEntity> historyB = new ArrayList<>(historyA.subList(0, 35));
        for (int i = 35; i < 40; i++) {
            historyB.add(draw(String.format(Locale.ROOT, "%07d", i + 1), 8, 8, 8));
        }

        when(fc3dPredictService.listHistoryAsc()).thenReturn(historyA);
        List<Fc3dEnsembleWeightOptimizer.PeriodSnapshot> snapshotsA = optimizer.buildSnapshots(versions, 5, 40);

        when(fc3dPredictService.listHistoryAsc()).thenReturn(historyB);
        List<Fc3dEnsembleWeightOptimizer.PeriodSnapshot> snapshotsB = optimizer.buildSnapshots(versions, 5, 40);

        assertEquals(35, snapshotsA.size());
        assertEquals(35, snapshotsB.size());

        // Snapshots for indices 5..33 (train never reaches the diverging tail, index >= 35) must be IDENTICAL.
        for (int i = 0; i < 29; i++) {
            assertEquals(snapshotsA.get(i).actualNumber, snapshotsB.get(i).actualNumber,
                    "snapshot #" + i + " must not depend on draws beyond its own train slice");
            assertEquals(toSimpleView(snapshotsA.get(i).members), toSimpleView(snapshotsB.get(i).members),
                    "snapshot #" + i + " candidates must not depend on draws beyond its own train slice");
        }
    }

    @Test
    void optimize_neverPerformsWorseThanCurrentBaseline() {
        when(fc3dPredictService.listHistoryAsc()).thenReturn(buildHistory(90));

        Fc3dEnsembleOptimizationResponse result = optimizer.optimize(versions, 20, 60);

        assertTrue(result.getAfter().getTop50() >= result.getBefore().getTop50() - 1e-9,
                "optimized Top50 must never be worse than the current baseline");
        assertTrue(result.getImprovement() >= -1e-9, "improvement must never be negative");
    }

    private String toSimpleView(List<Fc3dEnsembleMemberInput> members) {
        StringBuilder sb = new StringBuilder();
        for (Fc3dEnsembleMemberInput member : members) {
            sb.append(member.getModelVersion()).append('=');
            member.getCandidates().forEach(c -> sb.append(c.getNumber()).append(':').append(c.getScore()).append(','));
            sb.append(';');
        }
        return sb.toString();
    }

    private Map<String, Double> weights(double frequency, double missing, double sum, double oddEven, double span) {
        Map<String, Double> map = new java.util.LinkedHashMap<>();
        map.put("frequency", frequency);
        map.put("missing", missing);
        map.put("sum", sum);
        map.put("oddEven", oddEven);
        map.put("span", span);
        return map;
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
