package com.lottery.service;

import com.lottery.dto.Fc3dCombinationResponse;
import com.lottery.dto.Fc3dEnsembleCandidate;
import com.lottery.dto.Fc3dEnsembleMemberInput;
import com.lottery.dto.Fc3dEnsembleReportCoverage;
import com.lottery.dto.Fc3dEnsembleReportResponse;
import com.lottery.dto.Fc3dEnsembleReportStability;
import com.lottery.dto.Fc3dEnsembleReportTimeWindow;
import com.lottery.dto.Fc3dFrequencyResponse;
import com.lottery.dto.Fc3dMissingResponse;
import com.lottery.dto.Fc3dModelHealthCheck;
import com.lottery.dto.Fc3dSumAnalysisResponse;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.rule.fc3d.Fc3dCombinationGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sprint 11-C §1: read-only walk-forward performance report for the FC3D Ensemble — coverage
 * (Top10/20/50 hit rate), stability (average / volatility / max-drawdown of the Top50 hit rate
 * across calendar-quarter buckets) and an overall GOOD/WARNING/FAILED health verdict.
 *
 * <p>Purely a report: reuses the SAME walk-forward contract already used elsewhere ({@code
 * train = history.subList(0, i)}, never future data) and the SAME {@link Fc3dEnsembleEngine}
 * weighted-voting fusion already used by {@code /predict/ensemble}. Never generates numbers,
 * never mutates weights, never writes anything — advisory/observability only.</p>
 */
@Service
public class Fc3dEnsembleReportService {

    private static final int DEFAULT_MIN_HISTORY = 30;
    /** "评估周期: 最近1000期" — this report's default look-back window is far longer than the other ensemble endpoints'. */
    private static final int DEFAULT_EVAL_PERIODS = 1000;

    private static final double TOP50_GOOD_THRESHOLD = 0.30;
    private static final double TOP50_WARNING_THRESHOLD = 0.15;
    private static final double VOLATILITY_GOOD_THRESHOLD = 0.05;
    private static final double VOLATILITY_WARNING_THRESHOLD = 0.15;
    private static final double DRAWDOWN_GOOD_THRESHOLD = 0.10;
    private static final double DRAWDOWN_WARNING_THRESHOLD = 0.25;

    private final Fc3dPredictService fc3dPredictService;
    private final Fc3dAnalyticsService fc3dAnalyticsService;
    private final Fc3dModelRegistryService fc3dModelRegistryService;
    private final Fc3dEnsemblePredictService fc3dEnsemblePredictService;
    private final Fc3dEnsembleEngine fc3dEnsembleEngine;

    public Fc3dEnsembleReportService(Fc3dPredictService fc3dPredictService,
                                     Fc3dAnalyticsService fc3dAnalyticsService,
                                     Fc3dModelRegistryService fc3dModelRegistryService,
                                     Fc3dEnsemblePredictService fc3dEnsemblePredictService,
                                     Fc3dEnsembleEngine fc3dEnsembleEngine) {
        this.fc3dPredictService = fc3dPredictService;
        this.fc3dAnalyticsService = fc3dAnalyticsService;
        this.fc3dModelRegistryService = fc3dModelRegistryService;
        this.fc3dEnsemblePredictService = fc3dEnsemblePredictService;
        this.fc3dEnsembleEngine = fc3dEnsembleEngine;
    }

    public Fc3dEnsembleReportResponse generate(List<String> modelVersions, Integer minHistory, Integer evalPeriods) {
        List<String> versions = fc3dEnsemblePredictService.resolveVersions(modelVersions);
        int window = minHistory == null || minHistory <= 0 ? DEFAULT_MIN_HISTORY : minHistory;
        int periods = evalPeriods == null || evalPeriods <= 0 ? DEFAULT_EVAL_PERIODS : evalPeriods;

        Fc3dEnsembleReportResponse response = new Fc3dEnsembleReportResponse();
        response.setModelVersions(versions);
        response.setEnsembleLabel(ensembleLabel(versions));
        response.setCreatedTime(LocalDateTime.now());

        if (versions.isEmpty()) {
            return emptyReport(response, "没有可参与融合的模型（无 ACTIVE 注册模型）");
        }

        Map<String, Double> weights = fc3dEnsemblePredictService.resolveWeights(versions);
        List<Fc3dDrawEntity> history = fc3dPredictService.listHistoryAsc();
        if (history == null || history.size() <= window) {
            return emptyReport(response, "历史数据不足，无法进行 walk-forward 评估");
        }

        Map<String, Fc3dCombinationGenerator> generators = new LinkedHashMap<>();
        for (String version : versions) {
            generators.put(version, new Fc3dCombinationGenerator(fc3dModelRegistryService.resolveConfig(version)));
        }

        int rangeStart = Math.max(window, history.size() - periods);
        int rangeEnd = history.size();

        int hit10 = 0;
        int hit20 = 0;
        int hit50 = 0;
        Map<String, int[]> byQuarter = new LinkedHashMap<>(); // label -> [hit50, total]

        for (int i = rangeStart; i < rangeEnd; i++) {
            List<Fc3dDrawEntity> train = history.subList(0, i);
            Fc3dDrawEntity actual = history.get(i);
            String actualNumber = numberOf(actual);

            List<String> ranked = ensembleRanking(train, versions, generators, weights);

            boolean h10 = containsWithinTop(ranked, actualNumber, 10);
            boolean h20 = containsWithinTop(ranked, actualNumber, 20);
            boolean h50 = containsWithinTop(ranked, actualNumber, 50);
            if (h10) hit10++;
            if (h20) hit20++;
            if (h50) hit50++;

            String label = quarterLabel(actual.getDrawDate());
            int[] bucket = byQuarter.computeIfAbsent(label, k -> new int[2]);
            if (h50) bucket[0]++;
            bucket[1]++;
        }

        int evaluatedPeriods = rangeEnd - rangeStart;
        response.setEvaluatedPeriods(evaluatedPeriods);
        response.setCoverage(new Fc3dEnsembleReportCoverage(
                rate(hit10, evaluatedPeriods), rate(hit20, evaluatedPeriods), rate(hit50, evaluatedPeriods)));

        List<Fc3dEnsembleReportTimeWindow> timeWindows = new ArrayList<>();
        List<Double> quarterRates = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : byQuarter.entrySet()) {
            int[] bucket = entry.getValue();
            double windowRate = rate(bucket[0], bucket[1]);
            timeWindows.add(new Fc3dEnsembleReportTimeWindow(entry.getKey(), windowRate, bucket[1]));
            quarterRates.add(windowRate);
        }
        response.setTimeWindows(timeWindows);

        Fc3dEnsembleReportStability stability = computeStability(quarterRates);
        response.setStability(stability);

        List<Fc3dModelHealthCheck> checks = buildHealthChecks(response.getCoverage(), stability);
        response.setHealthChecks(checks);
        response.setHealth(worstStatus(checks));
        return response;
    }

    /**
     * Fans a SINGLE train slice out to every participating model's own generator, then fuses —
     * identical contract to {@code Fc3dEnsembleBacktestService}. Package-private (not {@code
     * private}) so {@code Fc3dEnsembleReportServiceTest} can verify the no-invented-number /
     * no-future-leakage invariants directly, same convention as {@code
     * Fc3dEnsembleWeightOptimizer#buildSnapshots}.
     */
    List<String> ensembleRanking(List<Fc3dDrawEntity> train, List<String> versions,
                                 Map<String, Fc3dCombinationGenerator> generators,
                                 Map<String, Double> weights) {
        Fc3dFrequencyResponse frequency = fc3dAnalyticsService.getPositionFrequency(train);
        Fc3dMissingResponse missing = fc3dAnalyticsService.calculateMissing(train);
        Fc3dSumAnalysisResponse sumAnalysis = fc3dAnalyticsService.calculateSumAnalysis(train, 30);

        List<Fc3dEnsembleMemberInput> members = new ArrayList<>(versions.size());
        for (String version : versions) {
            Fc3dCombinationResponse combinationResponse = generators.get(version).generate(train, frequency, missing, sumAnalysis);
            members.add(new Fc3dEnsembleMemberInput(version, combinationResponse.getCandidates()));
        }
        List<Fc3dEnsembleCandidate> fused = fc3dEnsembleEngine.fuseAll(members, weights);
        return fused.stream().map(Fc3dEnsembleCandidate::getNumber).toList();
    }

    /** Population stddev + running peak-to-trough drawdown of the per-quarter Top50 hit rate, in chronological order. */
    private Fc3dEnsembleReportStability computeStability(List<Double> quarterRates) {
        if (quarterRates.isEmpty()) {
            return new Fc3dEnsembleReportStability(0.0, 0.0, 0.0);
        }
        double average = quarterRates.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = quarterRates.stream().mapToDouble(r -> (r - average) * (r - average)).average().orElse(0.0);
        double volatility = Math.sqrt(variance);

        double peak = Double.NEGATIVE_INFINITY;
        double maxDrawdown = 0.0;
        for (double rate : quarterRates) {
            peak = Math.max(peak, rate);
            maxDrawdown = Math.max(maxDrawdown, peak - rate);
        }

        return new Fc3dEnsembleReportStability(round4(average), round4(volatility), round4(maxDrawdown));
    }

    private List<Fc3dModelHealthCheck> buildHealthChecks(Fc3dEnsembleReportCoverage coverage, Fc3dEnsembleReportStability stability) {
        List<Fc3dModelHealthCheck> checks = new ArrayList<>();

        String coverageStatus = coverage.getTop50() >= TOP50_GOOD_THRESHOLD ? Fc3dModelHealthService.GOOD
                : coverage.getTop50() >= TOP50_WARNING_THRESHOLD ? Fc3dModelHealthService.WARNING : Fc3dModelHealthService.FAILED;
        checks.add(new Fc3dModelHealthCheck("coverage", coverage.getTop50(), coverageStatus,
                "Top50 覆盖率 " + percent(coverage.getTop50())));

        String volatilityStatus = stability.getVolatility() <= VOLATILITY_GOOD_THRESHOLD ? Fc3dModelHealthService.GOOD
                : stability.getVolatility() <= VOLATILITY_WARNING_THRESHOLD ? Fc3dModelHealthService.WARNING : Fc3dModelHealthService.FAILED;
        checks.add(new Fc3dModelHealthCheck("volatility", stability.getVolatility(), volatilityStatus,
                "波动 ±" + percent(stability.getVolatility())));

        String drawdownStatus = stability.getMaxDrawdown() <= DRAWDOWN_GOOD_THRESHOLD ? Fc3dModelHealthService.GOOD
                : stability.getMaxDrawdown() <= DRAWDOWN_WARNING_THRESHOLD ? Fc3dModelHealthService.WARNING : Fc3dModelHealthService.FAILED;
        checks.add(new Fc3dModelHealthCheck("maxDrawdown", stability.getMaxDrawdown(), drawdownStatus,
                "最大回撤 " + percent(stability.getMaxDrawdown())));

        return checks;
    }

    private String worstStatus(List<Fc3dModelHealthCheck> checks) {
        String worst = Fc3dModelHealthService.GOOD;
        for (Fc3dModelHealthCheck check : checks) {
            if (Fc3dModelHealthService.FAILED.equals(check.getStatus())) {
                return Fc3dModelHealthService.FAILED;
            }
            if (Fc3dModelHealthService.WARNING.equals(check.getStatus())) {
                worst = Fc3dModelHealthService.WARNING;
            }
        }
        return worst;
    }

    private Fc3dEnsembleReportResponse emptyReport(Fc3dEnsembleReportResponse response, String reason) {
        response.setEvaluatedPeriods(0);
        response.setCoverage(new Fc3dEnsembleReportCoverage(0.0, 0.0, 0.0));
        response.setStability(new Fc3dEnsembleReportStability(0.0, 0.0, 0.0));
        response.setTimeWindows(List.of());
        response.setHealth(Fc3dModelHealthService.FAILED);
        response.setHealthChecks(List.of(new Fc3dModelHealthCheck("data", null, Fc3dModelHealthService.FAILED, reason)));
        return response;
    }

    private String ensembleLabel(List<String> versions) {
        if (versions.isEmpty()) {
            return "ensemble";
        }
        if (versions.size() == 1) {
            return "ensemble-" + versions.get(0);
        }
        return "ensemble(" + String.join("+", versions) + ")";
    }

    private String quarterLabel(LocalDate date) {
        if (date == null) {
            return "N/A";
        }
        int quarter = (date.getMonthValue() - 1) / 3 + 1;
        return date.getYear() + " Q" + quarter;
    }

    private String percent(double fraction) {
        return String.format(Locale.ROOT, "%.1f%%", fraction * 100.0);
    }

    private boolean containsWithinTop(List<String> ranked, String number, int limit) {
        int max = Math.min(limit, ranked.size());
        for (int i = 0; i < max; i++) {
            if (number.equals(ranked.get(i))) {
                return true;
            }
        }
        return false;
    }

    private String numberOf(Fc3dDrawEntity e) {
        return String.format(Locale.ROOT, "%d%d%d", safe(e.getDigit1()), safe(e.getDigit2()), safe(e.getDigit3()));
    }

    private int safe(Integer value) {
        return value != null ? value : 0;
    }

    private double rate(int hits, int total) {
        if (total == 0) {
            return 0.0;
        }
        return round4((double) hits / total);
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
