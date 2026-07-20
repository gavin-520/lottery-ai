package com.lottery.rule.fc3d;

import com.lottery.config.Fc3dModelConfig;
import com.lottery.dto.Fc3dCombinationCandidate;
import com.lottery.dto.Fc3dCombinationResponse;
import com.lottery.dto.Fc3dFrequencyResponse;
import com.lottery.dto.Fc3dMissingItem;
import com.lottery.dto.Fc3dMissingResponse;
import com.lottery.dto.Fc3dSumAnalysisResponse;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.util.Fc3dBallUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sprint 10-B: Top-N combination-pool generator for FC3D.
 *
 * <p>Unlike {@link Fc3dRuleEngine} (kept completely untouched — Sprint 9-B/10-A behavior
 * is unaffected), this generator scores the FULL FC3D number space (up to
 * {@link Fc3dModelConfig#getMaxPoolSize()}, capped at the 1000 possible three-digit
 * combinations 000-999) rather than a narrower per-position candidate pool, then
 * returns the top {@link Fc3dModelConfig#getCandidateCount()} by score.</p>
 *
 * <p>Numbers are never generated randomly — every combination's score is derived
 * purely from existing statistical signals (position frequency, missing, sum
 * interval, odd/even ratio, span, recent-repeat risk) weighted by the current
 * {@link Fc3dModelConfig}. This is statistical analysis only — never a guarantee
 * of any outcome.</p>
 */
@Component
public class Fc3dCombinationGenerator {

    private static final int RECENT_AVOID_DEFAULT = 5;

    private final Fc3dModelConfig config;

    public Fc3dCombinationGenerator(Fc3dModelConfig config) {
        this.config = config;
    }

    public Fc3dCombinationResponse generate(List<Fc3dDrawEntity> history,
                                            Fc3dFrequencyResponse frequency,
                                            Fc3dMissingResponse missing,
                                            Fc3dSumAnalysisResponse sumAnalysis) {
        Map<Integer, Integer> hundredsFreq = toIntMap(frequency != null ? frequency.getHundreds() : null);
        Map<Integer, Integer> tensFreq = toIntMap(frequency != null ? frequency.getTens() : null);
        Map<Integer, Integer> unitsFreq = toIntMap(frequency != null ? frequency.getUnits() : null);

        Map<Integer, Integer> hundredsMissing = missingByPosition(missing, "hundreds");
        Map<Integer, Integer> tensMissing = missingByPosition(missing, "tens");
        Map<Integer, Integer> unitsMissing = missingByPosition(missing, "units");

        Map<Integer, Integer> sumDist = parseSumDistribution(sumAnalysis);
        int sumMax = sumDist.values().stream().mapToInt(Integer::intValue).max().orElse(1);

        Map<String, Integer> oeDist = frequency != null && frequency.getOddEvenDistribution() != null
                ? frequency.getOddEvenDistribution() : Map.of();
        int oeMax = oeDist.values().stream().mapToInt(Integer::intValue).max().orElse(1);

        Map<Integer, Integer> spanDist = frequency != null && frequency.getSpanDistribution() != null
                ? frequency.getSpanDistribution() : Map.of();
        int spanMax = spanDist.values().stream().mapToInt(Integer::intValue).max().orElse(1);

        Set<String> recentNumbers = recentNumbers(history, RECENT_AVOID_DEFAULT);

        int poolSize = Math.max(1, Math.min(1000, config.getMaxPoolSize()));
        List<ScoredCombo> scored = new ArrayList<>(poolSize);
        outer:
        for (int h = 0; h <= 9; h++) {
            for (int t = 0; t <= 9; t++) {
                for (int u = 0; u <= 9; u++) {
                    if (scored.size() >= poolSize) {
                        break outer;
                    }
                    scored.add(scoreCombo(
                            h, t, u,
                            hundredsFreq, tensFreq, unitsFreq,
                            hundredsMissing, tensMissing, unitsMissing,
                            sumDist, sumMax,
                            oeDist, oeMax,
                            spanDist, spanMax,
                            recentNumbers
                    ));
                }
            }
        }

        scored.sort(Comparator
                .comparingDouble(ScoredCombo::rawScore).reversed()
                .thenComparing(ScoredCombo::number));

        double maxRaw = scored.isEmpty() ? 1.0 : Math.max(scored.get(0).rawScore, 1e-9);
        int candidateCount = Math.max(1, config.getCandidateCount());
        int limit = Math.min(candidateCount, scored.size());

        List<Fc3dCombinationCandidate> candidates = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            ScoredCombo combo = scored.get(i);
            int score = (int) Math.round(100.0 * combo.rawScore / maxRaw);
            score = Math.max(0, Math.min(100, score));
            String riskLevel = riskLevel(score, combo.repeated);
            candidates.add(new Fc3dCombinationCandidate(combo.number, score, i + 1, combo.reasons, riskLevel));
        }

        Fc3dCombinationResponse response = new Fc3dCombinationResponse();
        response.setLotteryType("FC3D");
        response.setModelVersion(config.getCombinationVersion());
        response.setTotalCandidates(candidates.size());
        response.setCandidates(candidates);
        return response;
    }

    private ScoredCombo scoreCombo(int h, int t, int u,
                                   Map<Integer, Integer> hf, Map<Integer, Integer> tf, Map<Integer, Integer> uf,
                                   Map<Integer, Integer> hm, Map<Integer, Integer> tm, Map<Integer, Integer> um,
                                   Map<Integer, Integer> sumDist, int sumMax,
                                   Map<String, Integer> oeDist, int oeMax,
                                   Map<Integer, Integer> spanDist, int spanMax,
                                   Set<String> recentNumbers) {
        String number = String.format(Locale.ROOT, "%d%d%d", h, t, u);
        List<String> reasons = new ArrayList<>();
        double raw = 0.0;

        // 1) Position frequency
        double freqPart = normCount(hf, h) + normCount(tf, t) + normCount(uf, u);
        raw += config.getWeightFrequency() * freqPart;
        if (isHot(hf, h)) {
            reasons.add("百位热号");
        }
        if (isHot(tf, t)) {
            reasons.add("十位热号");
        }
        if (isHot(uf, u)) {
            reasons.add("个位热号");
        }

        // 2) Missing signal
        double missPart = missBoost(hm.getOrDefault(h, 0))
                + missBoost(tm.getOrDefault(t, 0))
                + missBoost(um.getOrDefault(u, 0));
        raw += config.getWeightMissing() * missPart;
        if (hm.getOrDefault(h, 0) >= 8 || tm.getOrDefault(t, 0) >= 8 || um.getOrDefault(u, 0) >= 8) {
            reasons.add("存在遗漏偏高位，关注回补");
        }

        // 3) Sum interval
        int sum = Fc3dBallUtils.sum(h, t, u);
        double sumPart = sumMax <= 0 ? 0.0 : (double) sumDist.getOrDefault(sum, 0) / sumMax;
        raw += config.getWeightSum() * sumPart;
        if (sumPart >= 0.55) {
            reasons.add("和值" + sum + "高频区");
        } else if (sum >= 10 && sum <= 17) {
            reasons.add("和值" + sum + "落在常见中位区间");
        }

        // 4) Odd/even ratio
        String pattern = Fc3dBallUtils.oddEvenPattern(h, t, u);
        double oePart = oeMax <= 0 ? 0.0 : (double) oeDist.getOrDefault(pattern, 0) / oeMax;
        raw += config.getWeightOddEven() * oePart;
        if (oePart >= 0.45) {
            reasons.add("奇偶结构合理");
        } else if (countOdd(h, t, u) == 1 || countOdd(h, t, u) == 2) {
            reasons.add("奇偶比例相对均衡");
        }

        // 5) Span
        int span = Fc3dBallUtils.span(h, t, u);
        double spanPart = spanMax <= 0 ? 0.0 : (double) spanDist.getOrDefault(span, 0) / spanMax;
        raw += config.getWeightSpan() * spanPart;
        if (spanPart >= 0.45) {
            reasons.add("跨度" + span + "处于历史常见区间");
        }

        // 6) Recent-repeat risk
        boolean repeated = recentNumbers.contains(number);
        if (repeated) {
            raw *= config.getWeightAntiRepeatPenalty();
            reasons.add("近期重复风险，降权处理");
        } else {
            raw += config.getWeightAntiRepeatBonus();
            reasons.add("未简单复用近几期开奖号");
        }

        if (reasons.isEmpty()) {
            reasons.add("综合统计信号的候选组合");
        }

        List<String> uniqueReasons = reasons.stream().distinct().collect(Collectors.toCollection(ArrayList::new));
        return new ScoredCombo(number, raw, uniqueReasons, repeated);
    }

    private String riskLevel(int score, boolean repeated) {
        String tier;
        if (score >= 75) {
            tier = "LOW";
        } else if (score >= 50) {
            tier = "MEDIUM";
        } else {
            tier = "HIGH";
        }
        if (repeated) {
            // Recent-repeat risk bumps the tier up by one level.
            if ("LOW".equals(tier)) {
                tier = "MEDIUM";
            } else {
                tier = "HIGH";
            }
        }
        return tier;
    }

    private Set<String> recentNumbers(List<Fc3dDrawEntity> history, int n) {
        Set<String> set = new HashSet<>();
        if (history == null || history.isEmpty()) {
            return set;
        }
        int from = Math.max(0, history.size() - n);
        for (int i = from; i < history.size(); i++) {
            Fc3dDrawEntity d = history.get(i);
            if (d.getDigit1() != null && d.getDigit2() != null && d.getDigit3() != null) {
                set.add(String.format(Locale.ROOT, "%d%d%d", d.getDigit1(), d.getDigit2(), d.getDigit3()));
            }
        }
        return set;
    }

    private Map<Integer, Integer> missingByPosition(Fc3dMissingResponse missing, String position) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i <= 9; i++) {
            map.put(i, 0);
        }
        if (missing == null || missing.getItems() == null) {
            return map;
        }
        for (Fc3dMissingItem item : missing.getItems()) {
            if (position.equals(item.getPosition())) {
                map.put(item.getNumber(), item.getMissing());
            }
        }
        return map;
    }

    private Map<Integer, Integer> parseSumDistribution(Fc3dSumAnalysisResponse sumAnalysis) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        if (sumAnalysis == null || sumAnalysis.getDistribution() == null) {
            return map;
        }
        sumAnalysis.getDistribution().forEach((k, v) -> {
            try {
                map.put(Integer.parseInt(k), v);
            } catch (NumberFormatException ignored) {
                // skip
            }
        });
        return map;
    }

    private Map<Integer, Integer> toIntMap(Map<String, Integer> source) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i <= 9; i++) {
            map.put(i, 0);
        }
        if (source == null) {
            return map;
        }
        source.forEach((k, v) -> {
            try {
                map.put(Integer.parseInt(k), v != null ? v : 0);
            } catch (NumberFormatException ignored) {
                // skip
            }
        });
        return map;
    }

    private double normCount(Map<Integer, Integer> freq, int digit) {
        int max = freq.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        if (max <= 0) {
            return 0.0;
        }
        return (double) freq.getOrDefault(digit, 0) / max;
    }

    private boolean isHot(Map<Integer, Integer> freq, int digit) {
        int max = freq.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int count = freq.getOrDefault(digit, 0);
        return max > 0 && count >= Math.max(1, (int) Math.ceil(max * 0.85));
    }

    private double missBoost(int missing) {
        return Math.min(1.0, missing / 15.0);
    }

    private int countOdd(int h, int t, int u) {
        int odd = 0;
        if (h % 2 != 0) odd++;
        if (t % 2 != 0) odd++;
        if (u % 2 != 0) odd++;
        return odd;
    }

    private record ScoredCombo(String number, double rawScore, List<String> reasons, boolean repeated) {}
}
