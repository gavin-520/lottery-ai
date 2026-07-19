package com.lottery.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lottery.dto.BacktestRequest;
import com.lottery.dto.BacktestResponse;
import com.lottery.entity.BacktestReport;
import com.lottery.entity.LotteryHistory;
import com.lottery.mapper.BacktestReportMapper;
import com.lottery.rule.RuleEngine;
import com.lottery.util.LotteryBallUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BacktestService {

    private final HistoryService historyService;
    private final RuleEngine ruleEngine;
    private final BacktestReportMapper backtestReportMapper;
    private final ObjectMapper objectMapper;

    public BacktestService(HistoryService historyService,
                           RuleEngine ruleEngine,
                           BacktestReportMapper backtestReportMapper,
                           ObjectMapper objectMapper) {
        this.historyService = historyService;
        this.ruleEngine = ruleEngine;
        this.backtestReportMapper = backtestReportMapper;
        this.objectMapper = objectMapper;
    }

    public BacktestResponse run(BacktestRequest request) {
        List<LotteryHistory> all = historyService.listAllAsc();
        if (all.size() <= request.getMinHistory()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Need more history data (min " + (request.getMinHistory() + 1) + " periods)");
        }

        int totalPeriods = 0;
        int totalRedHits = 0;
        int maxRedHits = 0;
        int blueHits = 0;

        for (int i = request.getMinHistory(); i < all.size(); i++) {
            List<LotteryHistory> before = all.subList(0, i);
            LotteryHistory actual = all.get(i);

            RuleEngine.RuleScoreResult scores = ruleEngine.scoreAll(before);

            List<Integer> predictedRed = scores.redScores().entrySet().stream()
                    .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                    .limit(request.getTopRed())
                    .map(Map.Entry::getKey)
                    .toList();

            Integer predictedBlue = scores.blueScores().entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(1);

            List<Integer> actualRed = LotteryBallUtils.parseRedBalls(actual);
            int redHit = (int) actualRed.stream().filter(predictedRed::contains).count();
            totalRedHits += redHit;
            maxRedHits = Math.max(maxRedHits, redHit);
            if (predictedBlue.equals(LotteryBallUtils.parseBlueBall(actual))) {
                blueHits++;
            }
            totalPeriods++;
        }

        double avgRedHits = totalPeriods == 0 ? 0 : (double) totalRedHits / totalPeriods;
        double redHitRate = totalPeriods == 0 ? 0 : avgRedHits / 6.0;
        double blueHitRate = totalPeriods == 0 ? 0 : (double) blueHits / totalPeriods;

        Map<String, Object> summary = new HashMap<>();
        summary.put("minHistory", request.getMinHistory());
        summary.put("topRed", request.getTopRed());
        summary.put("avgRedHits", round(avgRedHits));
        summary.put("maxRedHits", maxRedHits);
        summary.put("blueHits", blueHits);

        BacktestReport report = new BacktestReport();
        report.setName(request.getName());
        report.setStartPeriod(all.get(request.getMinHistory()).getPeriod());
        report.setEndPeriod(all.get(all.size() - 1).getPeriod());
        report.setHitRate(BigDecimal.valueOf(round(redHitRate)));
        try {
            report.setSummary(objectMapper.writeValueAsString(summary));
        } catch (JsonProcessingException e) {
            report.setSummary("{}");
        }
        backtestReportMapper.insert(report);

        return new BacktestResponse(
                report.getId(),
                report.getName(),
                totalPeriods,
                totalRedHits,
                maxRedHits,
                round(avgRedHits),
                round(redHitRate),
                round(blueHitRate),
                summary
        );
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
