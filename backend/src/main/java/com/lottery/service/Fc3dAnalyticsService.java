package com.lottery.service;

import com.lottery.dto.BallFrequencyItem;
import com.lottery.dto.Fc3dAnalyticsSummary;
import com.lottery.dto.Fc3dFrequencyResponse;
import com.lottery.dto.Fc3dMissingItem;
import com.lottery.dto.Fc3dMissingResponse;
import com.lottery.dto.Fc3dOddEvenResponse;
import com.lottery.dto.Fc3dSumAnalysisResponse;
import com.lottery.dto.Fc3dSumTrendItem;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.util.Fc3dBallUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class Fc3dAnalyticsService {

    public static final String POS_HUNDREDS = "hundreds";
    public static final String POS_TENS = "tens";
    public static final String POS_UNITS = "units";

    private final Fc3dPredictService fc3dPredictService;

    public Fc3dAnalyticsService(Fc3dPredictService fc3dPredictService) {
        this.fc3dPredictService = fc3dPredictService;
    }

    /**
     * Position frequency with hundreds/tens/units maps.
     * Also fills legacy list fields for existing frontend compatibility.
     */
    @Cacheable(value = "fc3dAnalyticsFrequency")
    public Fc3dFrequencyResponse getPositionFrequency() {
        return getPositionFrequency(listHistory());
    }

    public Fc3dFrequencyResponse getPositionFrequency(List<Fc3dDrawEntity> history) {
        Map<Integer, Integer> hundreds = initDigitMap();
        Map<Integer, Integer> tens = initDigitMap();
        Map<Integer, Integer> units = initDigitMap();
        Map<Integer, Integer> digit = initDigitMap();
        Map<Integer, Integer> sumDist = new HashMap<>();
        Map<Integer, Integer> spanDist = new HashMap<>();
        Map<String, Integer> oeDist = new HashMap<>();

        for (Fc3dDrawEntity draw : history) {
            bump(digit, draw.getDigit1());
            bump(digit, draw.getDigit2());
            bump(digit, draw.getDigit3());
            bump(hundreds, draw.getDigit1());
            bump(tens, draw.getDigit2());
            bump(units, draw.getDigit3());
            if (draw.getSumValue() != null) {
                sumDist.merge(draw.getSumValue(), 1, Integer::sum);
            }
            if (draw.getSpanValue() != null) {
                spanDist.merge(draw.getSpanValue(), 1, Integer::sum);
            }
            if (draw.getOddEvenPattern() != null) {
                oeDist.merge(draw.getOddEvenPattern(), 1, Integer::sum);
            }
        }

        Fc3dFrequencyResponse response = new Fc3dFrequencyResponse();
        response.setTotalPeriods(history.size());
        response.setHundreds(toStringKeyMap(hundreds));
        response.setTens(toStringKeyMap(tens));
        response.setUnits(toStringKeyMap(units));
        response.setDigitFrequency(toItems(digit, "digit"));
        response.setPos1Frequency(toItems(hundreds, "pos1"));
        response.setPos2Frequency(toItems(tens, "pos2"));
        response.setPos3Frequency(toItems(units, "pos3"));
        response.setSumDistribution(sortedIntMap(sumDist));
        response.setSpanDistribution(sortedIntMap(spanDist));
        response.setOddEvenDistribution(oeDist.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new)));
        return response;
    }

    /** Legacy summary used by AgentService — kept for compatibility. */
    @Cacheable(value = "fc3dAnalytics")
    public Fc3dAnalyticsSummary getFrequencySummary() {
        Fc3dFrequencyResponse freq = getPositionFrequency();
        Fc3dAnalyticsSummary summary = new Fc3dAnalyticsSummary();
        summary.setTotalPeriods(freq.getTotalPeriods());
        summary.setDigitFrequency(freq.getDigitFrequency());
        summary.setPos1Frequency(freq.getPos1Frequency());
        summary.setPos2Frequency(freq.getPos2Frequency());
        summary.setPos3Frequency(freq.getPos3Frequency());
        summary.setSumDistribution(freq.getSumDistribution());
        summary.setSpanDistribution(freq.getSpanDistribution());
        summary.setOddEvenDistribution(freq.getOddEvenDistribution());
        return summary;
    }

    public Fc3dMissingResponse calculateMissing() {
        return calculateMissing(listHistory());
    }

    /**
     * Missing periods since each digit last appeared on each position.
     * History must be ascending by issue.
     */
    public Fc3dMissingResponse calculateMissing(List<Fc3dDrawEntity> history) {
        int[] lastHundreds = initLastSeen();
        int[] lastTens = initLastSeen();
        int[] lastUnits = initLastSeen();

        for (int i = 0; i < history.size(); i++) {
            Fc3dDrawEntity draw = history.get(i);
            if (draw.getDigit1() != null) {
                lastHundreds[draw.getDigit1()] = i;
            }
            if (draw.getDigit2() != null) {
                lastTens[draw.getDigit2()] = i;
            }
            if (draw.getDigit3() != null) {
                lastUnits[draw.getDigit3()] = i;
            }
        }

        int end = history.size();
        List<Fc3dMissingItem> items = new ArrayList<>();
        for (int n = 0; n <= 9; n++) {
            items.add(new Fc3dMissingItem(POS_HUNDREDS, n, missingOf(lastHundreds[n], end)));
            items.add(new Fc3dMissingItem(POS_TENS, n, missingOf(lastTens[n], end)));
            items.add(new Fc3dMissingItem(POS_UNITS, n, missingOf(lastUnits[n], end)));
        }
        items.sort(Comparator
                .comparingInt(Fc3dMissingItem::getMissing).reversed()
                .thenComparing(Fc3dMissingItem::getPosition)
                .thenComparingInt(Fc3dMissingItem::getNumber));

        Fc3dMissingResponse response = new Fc3dMissingResponse();
        response.setTotalPeriods(history.size());
        response.setItems(items);
        return response;
    }

    public Fc3dSumAnalysisResponse calculateSumAnalysis() {
        return calculateSumAnalysis(listHistory(), 30);
    }

    public Fc3dSumAnalysisResponse calculateSumAnalysis(List<Fc3dDrawEntity> history, int recentLimit) {
        Map<Integer, Integer> dist = new HashMap<>();
        long sumTotal = 0;
        for (Fc3dDrawEntity draw : history) {
            int sum = draw.getSumValue() != null
                    ? draw.getSumValue()
                    : Fc3dBallUtils.sum(
                    safeDigit(draw.getDigit1()),
                    safeDigit(draw.getDigit2()),
                    safeDigit(draw.getDigit3()));
            dist.merge(sum, 1, Integer::sum);
            sumTotal += sum;
        }

        double average = history.isEmpty() ? 0.0 : (double) sumTotal / history.size();
        average = Math.round(average * 10.0) / 10.0;

        List<Fc3dSumTrendItem> trend = new ArrayList<>();
        int from = Math.max(0, history.size() - Math.max(1, recentLimit));
        for (int i = from; i < history.size(); i++) {
            Fc3dDrawEntity draw = history.get(i);
            int sum = draw.getSumValue() != null
                    ? draw.getSumValue()
                    : Fc3dBallUtils.sum(
                    safeDigit(draw.getDigit1()),
                    safeDigit(draw.getDigit2()),
                    safeDigit(draw.getDigit3()));
            trend.add(new Fc3dSumTrendItem(draw.getIssue(), sum));
        }

        Map<String, Integer> distribution = new LinkedHashMap<>();
        sortedIntMap(dist).forEach((k, v) -> distribution.put(String.valueOf(k), v));

        Fc3dSumAnalysisResponse response = new Fc3dSumAnalysisResponse();
        response.setTotalPeriods(history.size());
        response.setAverage(average);
        response.setDistribution(distribution);
        response.setRecentTrend(trend);
        return response;
    }

    public Fc3dOddEvenResponse calculateOddEvenAnalysis() {
        return calculateOddEvenAnalysis(listHistory());
    }

    /** Odd/even of the latest draw (3 digits). */
    public Fc3dOddEvenResponse calculateOddEvenAnalysis(List<Fc3dDrawEntity> history) {
        Fc3dOddEvenResponse response = new Fc3dOddEvenResponse();
        if (history == null || history.isEmpty()) {
            response.setOddCount(0);
            response.setEvenCount(0);
            response.setPattern("");
            return response;
        }
        Fc3dDrawEntity latest = history.get(history.size() - 1);
        int d1 = safeDigit(latest.getDigit1());
        int d2 = safeDigit(latest.getDigit2());
        int d3 = safeDigit(latest.getDigit3());
        int odd = 0;
        int even = 0;
        for (int d : new int[]{d1, d2, d3}) {
            if (d % 2 == 0) {
                even++;
            } else {
                odd++;
            }
        }
        String pattern = latest.getOddEvenPattern();
        if (pattern == null || pattern.isBlank()) {
            pattern = Fc3dBallUtils.oddEvenPattern(d1, d2, d3);
        }
        response.setOddCount(odd);
        response.setEvenCount(even);
        response.setPattern(pattern.toUpperCase(Locale.ROOT));
        response.setIssue(latest.getIssue());
        return response;
    }

    private List<Fc3dDrawEntity> listHistory() {
        return fc3dPredictService.listHistoryAsc();
    }

    private int[] initLastSeen() {
        int[] arr = new int[10];
        for (int i = 0; i < 10; i++) {
            arr[i] = -1;
        }
        return arr;
    }

    private int missingOf(int lastIndex, int endExclusive) {
        if (lastIndex < 0) {
            return endExclusive;
        }
        return endExclusive - lastIndex - 1;
    }

    private Map<Integer, Integer> initDigitMap() {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i <= 9; i++) {
            map.put(i, 0);
        }
        return map;
    }

    private void bump(Map<Integer, Integer> map, Integer digit) {
        if (digit != null && digit >= 0 && digit <= 9) {
            map.merge(digit, 1, Integer::sum);
        }
    }

    private Map<String, Integer> toStringKeyMap(Map<Integer, Integer> source) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i <= 9; i++) {
            map.put(String.valueOf(i), source.getOrDefault(i, 0));
        }
        return map;
    }

    private List<BallFrequencyItem> toItems(Map<Integer, Integer> map, String type) {
        List<BallFrequencyItem> items = new ArrayList<>();
        map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> items.add(new BallFrequencyItem(e.getKey(), e.getValue(), type)));
        return items;
    }

    private Map<Integer, Integer> sortedIntMap(Map<Integer, Integer> source) {
        return source.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private int safeDigit(Integer digit) {
        return digit == null ? 0 : digit;
    }
}
