package com.lottery.rule.fc3d;

import com.lottery.config.Fc3dModelConfig;
import com.lottery.dto.Fc3dCandidate;
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
 * FC3D statistical candidate engine.
 * Scores are analytical signals only — not a guarantee of any outcome.
 */
@Component
public class Fc3dRuleEngine {

    public static final int DEFAULT_TOP_N = 10;

    private final Fc3dModelConfig config;

    public Fc3dRuleEngine(Fc3dModelConfig config) {
        this.config = config;
    }

    /**
     * Legacy per-position digit scores (kept for walk-forward backtest).
     */
    public Fc3dScoreResult scoreAll(List<Fc3dDrawEntity> history) {
        Map<Integer, Double> pos1 = initDigitScores();
        Map<Integer, Double> pos2 = initDigitScores();
        Map<Integer, Double> pos3 = initDigitScores();

        if (history == null || history.isEmpty()) {
            return new Fc3dScoreResult(pos1, pos2, pos3);
        }

        int n = history.size();
        for (int i = 0; i < n; i++) {
            Fc3dDrawEntity draw = history.get(i);
            double recency = 1.0 + (double) i / n;

            bump(pos1, draw.getDigit1(), 1.2 * recency);
            bump(pos2, draw.getDigit2(), 1.2 * recency);
            bump(pos3, draw.getDigit3(), 1.2 * recency);

            int sum = draw.getSumValue() != null ? draw.getSumValue() : 0;
            int span = draw.getSpanValue() != null ? draw.getSpanValue() : 0;
            if (sum >= 10 && sum <= 17) {
                bump(pos1, draw.getDigit1(), 0.3);
                bump(pos2, draw.getDigit2(), 0.3);
                bump(pos3, draw.getDigit3(), 0.3);
            }
            if (span >= 3 && span <= 7) {
                bump(pos1, draw.getDigit1(), 0.2);
                bump(pos3, draw.getDigit3(), 0.2);
            }

            String pattern = draw.getOddEvenPattern();
            if (pattern != null && pattern.length() >= 3) {
                applyOddEvenBias(pos1, draw.getDigit1(), pattern.charAt(0), 0.25);
                applyOddEvenBias(pos2, draw.getDigit2(), pattern.charAt(1), 0.25);
                applyOddEvenBias(pos3, draw.getDigit3(), pattern.charAt(2), 0.25);
            }
        }

        return new Fc3dScoreResult(pos1, pos2, pos3);
    }

    public List<Fc3dCandidate> generateCandidates(List<Fc3dDrawEntity> history,
                                                  Fc3dFrequencyResponse frequency,
                                                  Fc3dMissingResponse missing,
                                                  Fc3dSumAnalysisResponse sumAnalysis) {
        return generateCandidates(history, frequency, missing, sumAnalysis, DEFAULT_TOP_N);
    }

    /**
     * Build ranked candidates from analytics signals (frequency / missing / sum / odd-even / anti-repeat).
     */
    public List<Fc3dCandidate> generateCandidates(List<Fc3dDrawEntity> history,
                                                  Fc3dFrequencyResponse frequency,
                                                  Fc3dMissingResponse missing,
                                                  Fc3dSumAnalysisResponse sumAnalysis,
                                                  int topN) {
        if (history == null || history.isEmpty() || frequency == null) {
            return List.of(fallbackCandidate());
        }

        Map<Integer, Integer> hundredsFreq = toIntMap(frequency.getHundreds());
        Map<Integer, Integer> tensFreq = toIntMap(frequency.getTens());
        Map<Integer, Integer> unitsFreq = toIntMap(frequency.getUnits());

        Map<Integer, Integer> hundredsMissing = missingByPosition(missing, "hundreds");
        Map<Integer, Integer> tensMissing = missingByPosition(missing, "tens");
        Map<Integer, Integer> unitsMissing = missingByPosition(missing, "units");

        Map<Integer, Integer> sumDist = parseSumDistribution(sumAnalysis);
        int sumMax = sumDist.values().stream().mapToInt(Integer::intValue).max().orElse(1);

        Map<String, Integer> oeDist = frequency.getOddEvenDistribution() != null
                ? frequency.getOddEvenDistribution()
                : Map.of();
        int oeMax = oeDist.values().stream().mapToInt(Integer::intValue).max().orElse(1);

        Set<String> recentNumbers = recentNumbers(history, config.getRecentAvoidPeriods());

        List<Integer> poolH = digitPool(hundredsFreq, hundredsMissing);
        List<Integer> poolT = digitPool(tensFreq, tensMissing);
        List<Integer> poolU = digitPool(unitsFreq, unitsMissing);

        List<ScoredCombo> scored = new ArrayList<>();
        for (int h : poolH) {
            for (int t : poolT) {
                for (int u : poolU) {
                    scored.add(scoreCombo(
                            h, t, u,
                            hundredsFreq, tensFreq, unitsFreq,
                            hundredsMissing, tensMissing, unitsMissing,
                            sumDist, sumMax,
                            oeDist, oeMax,
                            recentNumbers
                    ));
                }
            }
        }

        scored.sort(Comparator
                .comparingDouble(ScoredCombo::rawScore).reversed()
                .thenComparing(ScoredCombo::number));

        double maxRaw = scored.isEmpty() ? 1.0 : Math.max(scored.get(0).rawScore, 1e-9);
        int limit = Math.max(1, topN);
        List<Fc3dCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, scored.size()); i++) {
            ScoredCombo combo = scored.get(i);
            int score = (int) Math.round(100.0 * combo.rawScore / maxRaw);
            score = Math.max(0, Math.min(100, score));
            candidates.add(new Fc3dCandidate(combo.number, score, combo.reasons));
        }
        if (candidates.isEmpty()) {
            candidates.add(fallbackCandidate());
        }
        return candidates;
    }

    private ScoredCombo scoreCombo(int h, int t, int u,
                                   Map<Integer, Integer> hf, Map<Integer, Integer> tf, Map<Integer, Integer> uf,
                                   Map<Integer, Integer> hm, Map<Integer, Integer> tm, Map<Integer, Integer> um,
                                   Map<Integer, Integer> sumDist, int sumMax,
                                   Map<String, Integer> oeDist, int oeMax,
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

        // 2) Missing signal (moderate boost for elevated missing)
        double missPart = missBoost(hm.getOrDefault(h, 0))
                + missBoost(tm.getOrDefault(t, 0))
                + missBoost(um.getOrDefault(u, 0));
        raw += config.getWeightMissing() * missPart;
        if (hm.getOrDefault(h, 0) >= 8) {
            reasons.add("百位遗漏偏高，关注回补");
        }
        if (tm.getOrDefault(t, 0) >= 8) {
            reasons.add("十位遗漏偏高，关注回补");
        }
        if (um.getOrDefault(u, 0) >= 8) {
            reasons.add("个位遗漏偏高，关注回补");
        }

        // 3) Sum reasonableness vs historical distribution
        int sum = Fc3dBallUtils.sum(h, t, u);
        double sumPart = sumMax <= 0 ? 0.0 : (double) sumDist.getOrDefault(sum, 0) / sumMax;
        raw += config.getWeightSum() * sumPart;
        if (sumPart >= 0.55) {
            reasons.add("和值处于历史高频区间");
        } else if (sum >= 10 && sum <= 17) {
            reasons.add("和值落在常见中位区间");
        }

        // 4) Odd/even structure
        String pattern = Fc3dBallUtils.oddEvenPattern(h, t, u);
        double oePart = oeMax <= 0 ? 0.0 : (double) oeDist.getOrDefault(pattern, 0) / oeMax;
        raw += config.getWeightOddEven() * oePart;
        if (oePart >= 0.45) {
            reasons.add("奇偶结构接近历史常见形态");
        } else if (countOdd(h, t, u) == 1 || countOdd(h, t, u) == 2) {
            reasons.add("奇偶比例相对均衡");
        }

        // 5) Avoid simple copy of recent draws
        if (recentNumbers.contains(number)) {
            raw *= config.getWeightAntiRepeatPenalty();
            reasons.add("近几期已出现，降权处理");
        } else {
            raw += config.getWeightAntiRepeatBonus();
            reasons.add("未简单复用近几期开奖号");
        }

        if (reasons.isEmpty()) {
            reasons.add("综合频率与形态的统计候选");
        }

        // Stable, de-duplicated reasons (preserve order)
        List<String> uniqueReasons = reasons.stream().distinct().collect(Collectors.toCollection(ArrayList::new));
        return new ScoredCombo(number, raw, uniqueReasons);
    }

    private List<Integer> digitPool(Map<Integer, Integer> freq, Map<Integer, Integer> missing) {
        Map<Integer, Double> rank = new HashMap<>();
        for (int d = 0; d <= 9; d++) {
            double f = normCount(freq, d);
            double m = missBoost(missing.getOrDefault(d, 0));
            rank.put(d, 0.7 * f + 0.3 * m);
        }
        return rank.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(config.getPoolPerPosition())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private Set<String> recentNumbers(List<Fc3dDrawEntity> history, int n) {
        Set<String> set = new HashSet<>();
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
        // Cap so extreme missing does not dominate
        return Math.min(1.0, missing / 15.0);
    }

    private int countOdd(int h, int t, int u) {
        int odd = 0;
        if (h % 2 != 0) odd++;
        if (t % 2 != 0) odd++;
        if (u % 2 != 0) odd++;
        return odd;
    }

    private Fc3dCandidate fallbackCandidate() {
        return new Fc3dCandidate("000", 50, List.of("历史样本不足，使用默认统计候选"));
    }

    private void applyOddEvenBias(Map<Integer, Double> scores, Integer digit, char oe, double weight) {
        if (digit == null) {
            return;
        }
        boolean odd = digit % 2 == 1;
        if ((oe == 'O' && odd) || (oe == 'E' && !odd)) {
            bump(scores, digit, weight);
        }
    }

    private void bump(Map<Integer, Double> scores, Integer digit, double delta) {
        if (digit == null || digit < 0 || digit > 9) {
            return;
        }
        scores.merge(digit, delta, Double::sum);
    }

    private Map<Integer, Double> initDigitScores() {
        Map<Integer, Double> scores = new HashMap<>();
        for (int i = 0; i <= 9; i++) {
            scores.put(i, 0.0);
        }
        return scores;
    }

    private record ScoredCombo(String number, double rawScore, List<String> reasons) {}

    public record Fc3dScoreResult(
            Map<Integer, Double> digit1Scores,
            Map<Integer, Double> digit2Scores,
            Map<Integer, Double> digit3Scores
    ) {}
}
