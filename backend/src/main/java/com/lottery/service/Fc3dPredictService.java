package com.lottery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lottery.config.Fc3dModelConfig;
import com.lottery.domain.LotteryType;
import com.lottery.dto.Fc3dCandidate;
import com.lottery.dto.Fc3dCombinationResponse;
import com.lottery.dto.Fc3dFrequencyResponse;
import com.lottery.dto.Fc3dMissingResponse;
import com.lottery.dto.Fc3dPredictResponse;
import com.lottery.dto.Fc3dSumAnalysisResponse;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.entity.PredictRecord;
import com.lottery.mapper.Fc3dDrawMapper;
import com.lottery.mapper.PredictRecordMapper;
import com.lottery.rule.fc3d.Fc3dCombinationGenerator;
import com.lottery.rule.fc3d.Fc3dRuleEngine;
import com.lottery.util.Fc3dBallUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class Fc3dPredictService {

    private final Fc3dDrawMapper fc3dDrawMapper;
    private final Fc3dRuleEngine fc3dRuleEngine;
    private final PredictRecordMapper predictRecordMapper;
    private final AiServiceClient aiServiceClient;
    private final Fc3dAnalyticsService fc3dAnalyticsService;
    private final Fc3dModelConfig fc3dModelConfig;
    private final Fc3dModelRegistryService fc3dModelRegistryService;

    public Fc3dPredictService(Fc3dDrawMapper fc3dDrawMapper,
                              Fc3dRuleEngine fc3dRuleEngine,
                              PredictRecordMapper predictRecordMapper,
                              AiServiceClient aiServiceClient,
                              @Lazy Fc3dAnalyticsService fc3dAnalyticsService,
                              Fc3dModelConfig fc3dModelConfig,
                              Fc3dModelRegistryService fc3dModelRegistryService) {
        this.fc3dDrawMapper = fc3dDrawMapper;
        this.fc3dRuleEngine = fc3dRuleEngine;
        this.predictRecordMapper = predictRecordMapper;
        this.aiServiceClient = aiServiceClient;
        this.fc3dAnalyticsService = fc3dAnalyticsService;
        this.fc3dModelConfig = fc3dModelConfig;
        this.fc3dModelRegistryService = fc3dModelRegistryService;
    }

    public List<Fc3dDrawEntity> listHistoryAsc() {
        return fc3dDrawMapper.selectList(new LambdaQueryWrapper<Fc3dDrawEntity>()
                .orderByAsc(Fc3dDrawEntity::getIssue));
    }

    public Fc3dPredictResponse predictByRules(String issue) {
        List<Fc3dDrawEntity> history = listHistoryAsc();
        List<Fc3dCandidate> candidates = buildCandidates(history);
        Fc3dCandidate best = candidates.get(0);
        int[] digits = parseDigits(best.getNumber());

        String target = issue != null && !issue.isBlank() ? issue : "next";
        return buildResponse(
                target,
                digits[0], digits[1], digits[2],
                fc3dModelConfig.getFullModelName(),
                scoreToConfidence(best.getScore()),
                "rules",
                candidates,
                best.getNumber()
        );
    }

    public Fc3dPredictResponse predictByAi(String issue) {
        List<Fc3dDrawEntity> history = listHistoryAsc();
        List<Fc3dCandidate> ruleCandidates = buildCandidates(history);
        Fc3dPredictResponse ai = aiServiceClient.predictFc3d(history, issue);
        if (ai != null) {
            String bestNumber = formatNumber(ai.getDigit1(), ai.getDigit2(), ai.getDigit3());
            List<Fc3dCandidate> merged = mergeAiIntoCandidates(ruleCandidates, bestNumber);
            ai.setCandidates(merged);
            ai.setBest(bestNumber);
            if (ai.getModelName() == null || ai.getModelName().isBlank()) {
                ai.setModelName("fc3d-ai-statistical");
            }
            ai.setModelVersion(fc3dModelConfig.getVersion());
            return ai;
        }
        Fc3dPredictResponse fallback = predictByRules(issue);
        fallback.setModelName("fc3d-statistical-model-fallback");
        fallback.setSource("rules-fallback");
        return fallback;
    }

    public Fc3dPredictResponse predictHybrid(String issue) {
        Fc3dPredictResponse rules = predictByRules(issue);
        Fc3dPredictResponse ai = predictByAi(issue);

        if ("rules-fallback".equals(ai.getSource()) || "rules".equals(ai.getSource())) {
            saveRecord(rules);
            return rules;
        }

        int d1 = ai.getDigit1() != null ? ai.getDigit1() : rules.getDigit1();
        int d2 = ai.getDigit2() != null ? ai.getDigit2() : rules.getDigit2();
        int d3 = ai.getDigit3() != null ? ai.getDigit3() : rules.getDigit3();
        String bestNumber = formatNumber(d1, d2, d3);
        List<Fc3dCandidate> merged = mergeAiIntoCandidates(
                rules.getCandidates() != null ? rules.getCandidates() : List.of(),
                bestNumber
        );

        Fc3dPredictResponse hybrid = buildResponse(
                rules.getIssue(),
                d1, d2, d3,
                "fc3d-hybrid-statistical-v1",
                0.70,
                "hybrid",
                merged,
                bestNumber
        );
        saveRecord(hybrid);
        return hybrid;
    }

    /**
     * Sprint 10-B: Top-N scored combination pool (default 50 of up to 1000 combinations).
     * Statistical analysis only — reuses the existing analytics + configured model weights,
     * never generates numbers randomly and never guarantees any outcome.
     *
     * <p>Sprint 10-D: defaults to whatever model {@link Fc3dModelRegistryService} currently
     * has promoted as the ACTIVE production version (flow: Predict → ModelSelector-informed
     * Active Model → CombinationGenerator → Top50). Falls back to the base {@link Fc3dModelConfig}
     * if no model has ever been registered, so pre-Sprint-10-D behavior is unchanged.</p>
     */
    public Fc3dCombinationResponse generateCombinations() {
        return generateCombinations(null);
    }

    /**
     * Sprint 10-D: same as {@link #generateCombinations()} but allows manually pinning a specific
     * registered model version, bypassing the currently-active model — the automatic model
     * selection loop never removes this manual override capability.
     */
    public Fc3dCombinationResponse generateCombinations(String modelVersion) {
        List<Fc3dDrawEntity> history = listHistoryAsc();
        Fc3dFrequencyResponse frequency = fc3dAnalyticsService.getPositionFrequency(history);
        Fc3dMissingResponse missing = fc3dAnalyticsService.calculateMissing(history);
        Fc3dSumAnalysisResponse sumAnalysis = fc3dAnalyticsService.calculateSumAnalysis(history, 30);
        Fc3dModelConfig effectiveConfig = fc3dModelRegistryService.resolveConfig(modelVersion);
        Fc3dCombinationGenerator generator = new Fc3dCombinationGenerator(effectiveConfig);
        return generator.generate(history, frequency, missing, sumAnalysis);
    }

    private List<Fc3dCandidate> buildCandidates(List<Fc3dDrawEntity> history) {
        Fc3dFrequencyResponse frequency = fc3dAnalyticsService.getPositionFrequency(history);
        Fc3dMissingResponse missing = fc3dAnalyticsService.calculateMissing(history);
        Fc3dSumAnalysisResponse sumAnalysis = fc3dAnalyticsService.calculateSumAnalysis(history, 30);
        List<Fc3dCandidate> candidates = fc3dRuleEngine.generateCandidates(
                history, frequency, missing, sumAnalysis);
        if (candidates == null || candidates.isEmpty()) {
            return List.of(new Fc3dCandidate("000", 50, List.of("历史样本不足，使用默认统计候选")));
        }
        return candidates;
    }

    private List<Fc3dCandidate> mergeAiIntoCandidates(List<Fc3dCandidate> base, String aiNumber) {
        List<Fc3dCandidate> merged = new ArrayList<>();
        boolean found = false;
        if (base != null) {
            for (Fc3dCandidate c : base) {
                if (aiNumber.equals(c.getNumber())) {
                    List<String> reasons = new ArrayList<>(c.getReasons() != null ? c.getReasons() : List.of());
                    if (reasons.stream().noneMatch(r -> r.contains("模型信号"))) {
                        reasons.add(0, "结合外部模型信号提升排序");
                    }
                    merged.add(new Fc3dCandidate(c.getNumber(), Math.min(100, c.getScore() + 3), reasons));
                    found = true;
                } else {
                    merged.add(c);
                }
            }
        }
        if (!found) {
            merged.add(0, new Fc3dCandidate(
                    aiNumber,
                    88,
                    List.of("外部模型推荐组合", "已并入统计分析候选集")
            ));
        }
        merged.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
        return merged;
    }

    private void saveRecord(Fc3dPredictResponse response) {
        PredictRecord record = new PredictRecord();
        record.setPeriod(response.getIssue());
        record.setLotteryType(LotteryType.FC3D.name());
        record.setModelName(response.getModelName());
        record.setRedBalls(response.getDigit1() + "," + response.getDigit2() + "," + response.getDigit3());
        record.setBlueBall("0");
        record.setConfidence(BigDecimal.valueOf(response.getConfidence() != null ? response.getConfidence() : 0.0));
        predictRecordMapper.insert(record);
    }

    private Fc3dPredictResponse buildResponse(String issue, int d1, int d2, int d3,
                                              String model, double confidence, String source,
                                              List<Fc3dCandidate> candidates, String best) {
        Fc3dPredictResponse response = new Fc3dPredictResponse();
        response.setLotteryType(LotteryType.FC3D.name());
        response.setIssue(issue);
        response.setDigit1(d1);
        response.setDigit2(d2);
        response.setDigit3(d3);
        response.setSumValue(Fc3dBallUtils.sum(d1, d2, d3));
        response.setSpanValue(Fc3dBallUtils.span(d1, d2, d3));
        response.setOddEvenPattern(Fc3dBallUtils.oddEvenPattern(d1, d2, d3));
        response.setModelName(model);
        response.setModelVersion(fc3dModelConfig.getVersion());
        response.setConfidence(confidence);
        response.setSource(source);
        response.setCandidates(candidates);
        response.setBest(best);
        return response;
    }

    private double scoreToConfidence(int score) {
        return Math.round((0.45 + score / 200.0) * 100.0) / 100.0;
    }

    private int[] parseDigits(String number) {
        if (number == null || number.length() < 3) {
            return new int[]{0, 0, 0};
        }
        return new int[]{
                Character.getNumericValue(number.charAt(0)),
                Character.getNumericValue(number.charAt(1)),
                Character.getNumericValue(number.charAt(2))
        };
    }

    private String formatNumber(Integer d1, Integer d2, Integer d3) {
        return String.format(Locale.ROOT, "%d%d%d",
                d1 != null ? d1 : 0,
                d2 != null ? d2 : 0,
                d3 != null ? d3 : 0);
    }
}
