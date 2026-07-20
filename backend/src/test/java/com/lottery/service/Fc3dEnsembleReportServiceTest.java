package com.lottery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lottery.config.Fc3dModelConfig;
import com.lottery.dto.Fc3dEnsembleReportResponse;
import com.lottery.dto.Fc3dModelHealthCheck;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.rule.fc3d.Fc3dCombinationGenerator;
import com.lottery.util.Fc3dBallUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Sprint 11-C §1: verifies {@link Fc3dEnsembleReportService} — the read-only walk-forward
 * Ensemble performance report (coverage / stability / time windows / health). Never asserts on
 * any particular winning number, only on the report contract itself.
 */
@ExtendWith(MockitoExtension.class)
class Fc3dEnsembleReportServiceTest {

    @Mock
    private Fc3dPredictService fc3dPredictService;

    @Mock
    private Fc3dModelMetricService fc3dModelMetricService;

    private Fc3dModelRegistryService registryService;
    private Fc3dEnsembleReportService reportService;
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
        registryService.register("v3-exp-001", weights(0.10, 0.10, 0.10, 0.10, 0.60));

        Fc3dAnalyticsService analyticsService = new Fc3dAnalyticsService(null);
        Fc3dEnsembleEngine engine = new Fc3dEnsembleEngine();
        Fc3dEnsembleWeightOverrideService overrideService = new Fc3dEnsembleWeightOverrideService();
        Fc3dEnsemblePredictService ensemblePredictService =
                new Fc3dEnsemblePredictService(fc3dPredictService, analyticsService, registryService, engine, overrideService);
        reportService = new Fc3dEnsembleReportService(
                fc3dPredictService, analyticsService, registryService, ensemblePredictService, engine);

        versions = List.of("v3", "v3-exp-001");
    }

    @Test
    void generate_coverageIsMonotonic_top10_le_top20_le_top50() {
        when(fc3dPredictService.listHistoryAsc()).thenReturn(buildHistory(120));

        Fc3dEnsembleReportResponse report = reportService.generate(versions, 20, 90);

        assertTrue(report.getCoverage().getTop10() <= report.getCoverage().getTop20() + 1e-9,
                "Top10 hit rate must never exceed Top20");
        assertTrue(report.getCoverage().getTop20() <= report.getCoverage().getTop50() + 1e-9,
                "Top20 hit rate must never exceed Top50");
    }

    @Test
    void generate_isDeterministic_forSameHistory() {
        when(fc3dPredictService.listHistoryAsc()).thenReturn(buildHistory(120));

        Fc3dEnsembleReportResponse first = reportService.generate(versions, 20, 90);
        Fc3dEnsembleReportResponse second = reportService.generate(versions, 20, 90);

        assertEquals(first.getCoverage().getTop50(), second.getCoverage().getTop50());
        assertEquals(first.getStability().getAverage(), second.getStability().getAverage());
        assertEquals(first.getStability().getVolatility(), second.getStability().getVolatility());
        assertEquals(first.getStability().getMaxDrawdown(), second.getStability().getMaxDrawdown());
        assertEquals(first.getTimeWindows(), second.getTimeWindows());
        assertEquals(first.getHealth(), second.getHealth());
    }

    @Test
    void generate_timeWindowPeriodsSumToEvaluatedPeriods() {
        when(fc3dPredictService.listHistoryAsc()).thenReturn(buildHistory(120));

        Fc3dEnsembleReportResponse report = reportService.generate(versions, 20, 90);

        int sum = report.getTimeWindows().stream().mapToInt(w -> w.getEvaluatedPeriods()).sum();
        assertEquals(report.getEvaluatedPeriods(), sum,
                "every evaluated period must fall into exactly one time window bucket");
    }

    @Test
    void generate_stabilityMetricsAreNonNegative_andHealthIsRecognized() {
        when(fc3dPredictService.listHistoryAsc()).thenReturn(buildHistory(120));

        Fc3dEnsembleReportResponse report = reportService.generate(versions, 20, 90);

        assertTrue(report.getStability().getVolatility() >= 0.0, "volatility must never be negative");
        assertTrue(report.getStability().getMaxDrawdown() >= 0.0, "max drawdown must never be negative");
        assertTrue(List.of("GOOD", "WARNING", "FAILED").contains(report.getHealth()),
                "health must be one of GOOD/WARNING/FAILED");
        for (Fc3dModelHealthCheck check : report.getHealthChecks()) {
            assertTrue(List.of("GOOD", "WARNING", "FAILED").contains(check.getStatus()));
        }
    }

    @Test
    void ensembleRanking_neverProducesNumbersAbsentFromEveryMemberCandidateList() {
        List<Fc3dDrawEntity> history = buildHistory(60);
        List<Fc3dDrawEntity> train = history.subList(0, 40);
        Map<String, Fc3dCombinationGenerator> generators = new LinkedHashMap<>();
        for (String v : versions) {
            generators.put(v, new Fc3dCombinationGenerator(registryService.resolveConfig(v)));
        }
        Map<String, Double> weights = Map.of("v3", 0.6, "v3-exp-001", 0.4);

        List<String> ranked = reportService.ensembleRanking(train, versions, generators, weights);

        java.util.Set<String> allowed = new java.util.HashSet<>();
        for (String v : versions) {
            generators.get(v).generate(train,
                    new Fc3dAnalyticsService(null).getPositionFrequency(train),
                    new Fc3dAnalyticsService(null).calculateMissing(train),
                    new Fc3dAnalyticsService(null).calculateSumAnalysis(train, 30))
                    .getCandidates().forEach(c -> allowed.add(c.getNumber()));
        }
        for (String number : ranked) {
            assertTrue(allowed.contains(number), "report must never rank a number no member model proposed: " + number);
        }
    }

    @Test
    void ensembleRanking_neverLeaksFutureData_sharedPrefixYieldsIdenticalRanking() {
        List<Fc3dDrawEntity> historyA = buildHistory(40);
        List<Fc3dDrawEntity> trainA = historyA.subList(0, 30);

        List<Fc3dDrawEntity> historyB = new ArrayList<>(historyA.subList(0, 30));
        for (int i = 30; i < 40; i++) {
            historyB.add(draw(String.format(Locale.ROOT, "%07d", i + 1), 9, 9, 9, LocalDate.of(2025, 1, 1).plusDays(i)));
        }
        List<Fc3dDrawEntity> trainB = historyB.subList(0, 30);

        Map<String, Fc3dCombinationGenerator> generators = new LinkedHashMap<>();
        for (String v : versions) {
            generators.put(v, new Fc3dCombinationGenerator(registryService.resolveConfig(v)));
        }
        Map<String, Double> weights = Map.of("v3", 0.6, "v3-exp-001", 0.4);

        List<String> rankedA = reportService.ensembleRanking(trainA, versions, generators, weights);
        List<String> rankedB = reportService.ensembleRanking(trainB, versions, generators, weights);

        assertEquals(rankedA, rankedB, "identical train slices (regardless of what comes after) must yield identical rankings");
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

    private List<Fc3dDrawEntity> buildHistory(int n) {
        List<Fc3dDrawEntity> list = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 1, 1);
        for (int i = 0; i < n; i++) {
            int d1 = i % 10;
            int d2 = (i * 3) % 10;
            int d3 = (i * 7 + 2) % 10;
            list.add(draw(String.format(Locale.ROOT, "%07d", i + 1), d1, d2, d3, start.plusDays(i * 3L)));
        }
        return list;
    }

    private Fc3dDrawEntity draw(String issue, int d1, int d2, int d3, LocalDate drawDate) {
        Fc3dDrawEntity e = new Fc3dDrawEntity();
        e.setIssue(issue);
        e.setDigit1(d1);
        e.setDigit2(d2);
        e.setDigit3(d3);
        e.setSumValue(Fc3dBallUtils.sum(d1, d2, d3));
        e.setSpanValue(Fc3dBallUtils.span(d1, d2, d3));
        e.setOddEvenPattern(Fc3dBallUtils.oddEvenPattern(d1, d2, d3));
        e.setLotteryType("FC3D");
        e.setDrawDate(drawDate);
        return e;
    }
}
