package com.lottery.service;

import com.lottery.dto.PredictResponse;
import com.lottery.entity.LotteryHistory;
import com.lottery.entity.PredictRecord;
import com.lottery.mapper.PredictRecordMapper;
import com.lottery.rule.RuleEngine;
import com.lottery.context.CorrelationIdContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PredictService {

    private final HistoryService historyService;
    private final RuleEngine ruleEngine;
    private final AiServiceClient aiServiceClient;
    private final PredictRecordMapper predictRecordMapper;
    private final PlatformEventService platformEventService;

    public PredictService(HistoryService historyService,
                          RuleEngine ruleEngine,
                          AiServiceClient aiServiceClient,
                          PredictRecordMapper predictRecordMapper,
                          PlatformEventService platformEventService) {
        this.historyService = historyService;
        this.ruleEngine = ruleEngine;
        this.aiServiceClient = aiServiceClient;
        this.predictRecordMapper = predictRecordMapper;
        this.platformEventService = platformEventService;
    }

    public PredictResponse predictByRules(String period) {
        List<LotteryHistory> history = historyService.listAllAsc();
        RuleEngine.RuleScoreResult scores = ruleEngine.scoreAll(history);

        List<Integer> redBalls = scores.redScores().entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(6)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());

        Integer blueBall = scores.blueScores().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(1);

        String targetPeriod = period != null ? period : "next";
        return new PredictResponse(targetPeriod, redBalls, blueBall, "rule-engine-v1", 0.65, "rules");
    }

    public PredictResponse predictByAi(String period) {
        List<LotteryHistory> history = historyService.listAllAsc();
        PredictResponse aiResult = aiServiceClient.predict(history, period);
        if (aiResult != null) {
            return aiResult;
        }
        PredictResponse fallback = predictByRules(period);
        return new PredictResponse(
                fallback.getPeriod(),
                fallback.getRedBalls(),
                fallback.getBlueBall(),
                "rule-engine-fallback",
                fallback.getConfidence(),
                "rules-fallback"
        );
    }

    public PredictResponse predictHybrid(String period) {
        PredictResponse rules = predictByRules(period);
        PredictResponse ai = predictByAi(period);

        if ("rules-fallback".equals(ai.getSource()) || "rules".equals(ai.getSource())) {
            saveRecord(ai);
            return ai;
        }

        List<Integer> mergedRed = mergeRedBalls(rules.getRedBalls(), ai.getRedBalls());
        PredictResponse hybrid = new PredictResponse(
                ai.getPeriod(),
                mergedRed,
                ai.getBlueBall(),
                "hybrid-v1",
                (rules.getConfidence() + ai.getConfidence()) / 2,
                "hybrid"
        );
        saveRecord(hybrid);
        return hybrid;
    }

    private List<Integer> mergeRedBalls(List<Integer> rules, List<Integer> ai) {
        return java.util.stream.Stream.concat(rules.stream(), ai.stream())
                .distinct()
                .sorted()
                .limit(6)
                .collect(Collectors.toList());
    }

    private void saveRecord(PredictResponse response) {
        PredictRecord record = new PredictRecord();
        record.setPeriod(response.getPeriod());
        record.setModelName(response.getModelName());
        record.setRedBalls(response.getRedBalls().stream().map(String::valueOf).collect(Collectors.joining(",")));
        record.setBlueBall(String.valueOf(response.getBlueBall()));
        record.setConfidence(BigDecimal.valueOf(response.getConfidence()));
        predictRecordMapper.insert(record);

        Map<String, Object> event = new HashMap<>();
        event.put("period", response.getPeriod());
        event.put("modelName", response.getModelName());
        event.put("source", response.getSource());
        event.put("confidence", response.getConfidence());
        platformEventService.publishPredictEvent(event, CorrelationIdContext.getOrGenerate());
    }
}
