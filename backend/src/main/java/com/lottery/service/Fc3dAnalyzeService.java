package com.lottery.service;

import com.lottery.dto.Fc3dAnalyzeResponse;
import com.lottery.dto.Fc3dCandidate;
import com.lottery.dto.Fc3dCandidateAnalysisItem;
import com.lottery.dto.Fc3dCandidateCoverage;
import com.lottery.dto.Fc3dCombinationCandidate;
import com.lottery.dto.Fc3dCombinationResponse;
import com.lottery.dto.Fc3dFeatureSummary;
import com.lottery.dto.Fc3dFrequencyResponse;
import com.lottery.dto.Fc3dMissingResponse;
import com.lottery.dto.Fc3dOddEvenResponse;
import com.lottery.dto.Fc3dPredictResponse;
import com.lottery.dto.Fc3dRecommendation;
import com.lottery.dto.Fc3dScoreRange;
import com.lottery.dto.Fc3dSumAnalysisResponse;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.rule.fc3d.Fc3dCombinationGenerator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Explains FC3D statistical candidates produced by {@link com.lottery.rule.fc3d.Fc3dRuleEngine}.
 * This service NEVER generates new numbers — it only interprets existing analytics and candidates,
 * either via the AI service or a local rule-based fallback with the same contract.
 */
@Service
public class Fc3dAnalyzeService {

    public static final String DISCLAIMER = "本结果仅供统计分析与研究参考，不构成中奖承诺或购彩建议。";
    private static final int RECENT_HISTORY_FOR_AI = 20;

    private final Fc3dAnalyticsService fc3dAnalyticsService;
    private final Fc3dPredictService fc3dPredictService;
    private final AiServiceClient aiServiceClient;
    private final Fc3dCombinationGenerator fc3dCombinationGenerator;

    public Fc3dAnalyzeService(Fc3dAnalyticsService fc3dAnalyticsService,
                              Fc3dPredictService fc3dPredictService,
                              AiServiceClient aiServiceClient,
                              Fc3dCombinationGenerator fc3dCombinationGenerator) {
        this.fc3dAnalyticsService = fc3dAnalyticsService;
        this.fc3dPredictService = fc3dPredictService;
        this.aiServiceClient = aiServiceClient;
        this.fc3dCombinationGenerator = fc3dCombinationGenerator;
    }

    public Fc3dAnalyzeResponse analyze(String issue, String question) {
        List<Fc3dDrawEntity> history = fc3dPredictService.listHistoryAsc();
        Fc3dFrequencyResponse frequency = fc3dAnalyticsService.getPositionFrequency(history);
        Fc3dMissingResponse missing = fc3dAnalyticsService.calculateMissing(history);
        Fc3dSumAnalysisResponse sumAnalysis = fc3dAnalyticsService.calculateSumAnalysis(history, 30);
        Fc3dOddEvenResponse oddEven = fc3dAnalyticsService.calculateOddEvenAnalysis(history);
        Fc3dPredictResponse predicted = fc3dPredictService.predictByRules(issue);

        List<Fc3dDrawEntity> recent = history.subList(
                Math.max(0, history.size() - RECENT_HISTORY_FOR_AI), history.size());

        // Sprint 10-B: Top50 combination-pool coverage, additive on every analyze() response.
        Fc3dCandidateCoverage coverage = buildCandidateCoverage(history, frequency, missing, sumAnalysis);

        Fc3dAnalyzeResponse ai = aiServiceClient.analyzeFc3d(
                frequency, missing, sumAnalysis, oddEven,
                predicted.getCandidates(), predicted.getBest(),
                recent, issue, question);
        if (ai != null) {
            ai.setCandidateCoverage(coverage);
            return ai;
        }

        Fc3dAnalyzeResponse fallback = buildFallback(frequency, sumAnalysis, oddEven, predicted, history.size(), question);
        fallback.setCandidateCoverage(coverage);
        return fallback;
    }

    /**
     * Sprint 10-B: summarizes the Top50 combination pool (total / score range / risk
     * distribution) for the analyze() response. Does NOT generate new numbers.
     */
    private Fc3dCandidateCoverage buildCandidateCoverage(List<Fc3dDrawEntity> history,
                                                         Fc3dFrequencyResponse frequency,
                                                         Fc3dMissingResponse missing,
                                                         Fc3dSumAnalysisResponse sumAnalysis) {
        Fc3dCombinationResponse combinations = fc3dCombinationGenerator.generate(history, frequency, missing, sumAnalysis);
        List<Fc3dCombinationCandidate> candidates = combinations.getCandidates() != null
                ? combinations.getCandidates() : List.of();

        int minScore = candidates.stream().mapToInt(Fc3dCombinationCandidate::getScore).min().orElse(0);
        int maxScore = candidates.stream().mapToInt(Fc3dCombinationCandidate::getScore).max().orElse(0);
        Map<String, Integer> riskDistribution = new LinkedHashMap<>();
        for (Fc3dCombinationCandidate candidate : candidates) {
            riskDistribution.merge(candidate.getRiskLevel(), 1, Integer::sum);
        }

        Fc3dCandidateCoverage coverage = new Fc3dCandidateCoverage();
        coverage.setTotal(combinations.getTotalCandidates());
        coverage.setScoreRange(new Fc3dScoreRange(minScore, maxScore));
        coverage.setRiskDistribution(riskDistribution);
        return coverage;
    }

    private Fc3dAnalyzeResponse buildFallback(Fc3dFrequencyResponse frequency,
                                              Fc3dSumAnalysisResponse sumAnalysis,
                                              Fc3dOddEvenResponse oddEven,
                                              Fc3dPredictResponse predicted,
                                              int totalPeriods,
                                              String question) {
        Fc3dFeatureSummary features = new Fc3dFeatureSummary();
        Map<String, List<Integer>> hotDigits = new LinkedHashMap<>();
        hotDigits.put("hundreds", topDigits(frequency.getHundreds()));
        hotDigits.put("tens", topDigits(frequency.getTens()));
        hotDigits.put("units", topDigits(frequency.getUnits()));
        features.setHotDigits(hotDigits);
        features.setSumAverage(sumAnalysis.getAverage());
        features.setDominantOddEven(oddEven.getPattern());
        List<String> notes = new ArrayList<>();
        notes.add("基于历史频率、和值与奇偶形态的统计摘要");
        notes.add("参考共 " + totalPeriods + " 期历史数据（本地规则兜底，AI 服务暂不可用）");
        features.setNotes(notes);

        List<Fc3dCandidate> candidates = predicted.getCandidates() != null
                ? predicted.getCandidates() : List.of();
        List<Fc3dCandidateAnalysisItem> candidateAnalysis = new ArrayList<>();
        for (Fc3dCandidate candidate : candidates) {
            candidateAnalysis.add(explainCandidate(candidate));
        }

        Fc3dRecommendation recommendation = new Fc3dRecommendation();
        recommendation.setPreferred(predicted.getBest());
        List<String> rationale = new ArrayList<>();
        candidateAnalysis.stream()
                .filter(c -> c.getNumber().equals(predicted.getBest()))
                .findFirst()
                .ifPresentOrElse(top -> {
                    rationale.add("综合评分在候选集中相对领先");
                    top.getAlignedSignals().stream().limit(3)
                            .forEach(s -> rationale.add("信号契合：" + s));
                    if (!top.getRiskFlags().isEmpty()) {
                        rationale.add("需留意：" + String.join("；", top.getRiskFlags()));
                    }
                }, () -> rationale.add("候选样本不足，建议结合更多历史数据观察"));
        if (question != null && !question.isBlank()) {
            rationale.add("已结合问题「" + question + "」进行统计口径说明");
        }
        recommendation.setRationale(rationale);
        recommendation.setDisclaimer(DISCLAIMER);

        double confidence = candidateAnalysis.stream()
                .mapToInt(Fc3dCandidateAnalysisItem::getScore)
                .max().stream()
                .mapToDouble(score -> Math.min(0.85, Math.max(0.4, 0.45 + (score / 100.0) * 0.35)))
                .findFirst().orElse(0.5);
        confidence = Math.round(confidence * 100.0) / 100.0;

        Fc3dAnalyzeResponse response = new Fc3dAnalyzeResponse();
        response.setLotteryType("FC3D");
        response.setFeatures(features);
        response.setCandidateAnalysis(candidateAnalysis);
        response.setRecommendation(recommendation);
        response.setConfidence(confidence);
        response.setModelName("fc3d-analyze-fallback-v1");
        return response;
    }

    private Fc3dCandidateAnalysisItem explainCandidate(Fc3dCandidate candidate) {
        List<String> reasons = candidate.getReasons() != null ? candidate.getReasons() : List.of();
        List<String> alignedSignals = mapReasonsToSignals(reasons);
        List<String> riskFlags = riskFlags(candidate.getNumber(), reasons);

        StringBuilder comment = new StringBuilder("候选 ").append(candidate.getNumber())
                .append(" 综合评分 ").append(candidate.getScore());
        if (!alignedSignals.isEmpty()) {
            comment.append("，契合信号：").append(String.join("、", alignedSignals));
        }
        if (!riskFlags.isEmpty()) {
            comment.append("，风险提示：").append(String.join("；", riskFlags));
        }
        comment.append("，该结果为统计分析与概率模型解释，不构成中奖承诺");

        Fc3dCandidateAnalysisItem item = new Fc3dCandidateAnalysisItem();
        item.setNumber(candidate.getNumber());
        item.setScore(candidate.getScore());
        item.setAlignedSignals(alignedSignals);
        item.setRiskFlags(riskFlags);
        item.setComment(comment.toString());
        return item;
    }

    private List<String> mapReasonsToSignals(List<String> reasons) {
        Map<String, String> signalMap = new LinkedHashMap<>();
        signalMap.put("热号", "位频率");
        signalMap.put("遗漏", "遗漏值");
        signalMap.put("和值", "和值区间");
        signalMap.put("奇偶", "奇偶结构");
        signalMap.put("复用", "历史重复模式");

        List<String> signals = new ArrayList<>();
        for (String reason : reasons) {
            for (Map.Entry<String, String> entry : signalMap.entrySet()) {
                if (reason.contains(entry.getKey()) && !signals.contains(entry.getValue())) {
                    signals.add(entry.getValue());
                }
            }
        }
        return signals;
    }

    private List<String> riskFlags(String number, List<String> reasons) {
        List<String> flags = new ArrayList<>();
        boolean deWeighted = reasons.stream().anyMatch(r -> r.contains("降权") || r.contains("已出现"));
        if (deWeighted) {
            flags.add("与近期开奖号码相似，统计上重复概率降低");
        }
        if (number != null && number.length() == 3) {
            Set<Character> unique = new HashSet<>();
            for (char c : number.toCharArray()) {
                unique.add(c);
            }
            if (unique.size() == 1) {
                flags.add("三位数字相同（豹子号），历史出现概率较低");
            }
        }
        return flags;
    }

    private List<Integer> topDigits(Map<String, Integer> freqMap) {
        if (freqMap == null || freqMap.isEmpty()) {
            return List.of();
        }
        return freqMap.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed())
                .limit(3)
                .map(e -> Integer.parseInt(e.getKey()))
                .toList();
    }
}
