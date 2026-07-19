package com.lottery.service;

import com.lottery.dto.AnalyticsSummary;
import com.lottery.dto.BallFrequencyItem;
import com.lottery.entity.LotteryHistory;
import com.lottery.util.LotteryBallUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {

    private final HistoryService historyService;

    public AnalyticsService(HistoryService historyService) {
        this.historyService = historyService;
    }

    @Cacheable(value = "analytics")
    public AnalyticsSummary getFrequencySummary() {
        List<LotteryHistory> history = historyService.listAllAsc();
        Map<Integer, Integer> redCount = new HashMap<>();
        Map<Integer, Integer> blueCount = new HashMap<>();

        for (int i = 1; i <= 33; i++) {
            redCount.put(i, 0);
        }
        for (int i = 1; i <= 16; i++) {
            blueCount.put(i, 0);
        }

        for (LotteryHistory record : history) {
            for (Integer ball : LotteryBallUtils.parseRedBalls(record)) {
                redCount.merge(ball, 1, Integer::sum);
            }
            blueCount.merge(LotteryBallUtils.parseBlueBall(record), 1, Integer::sum);
        }

        List<BallFrequencyItem> redFrequency = new ArrayList<>();
        redCount.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> redFrequency.add(new BallFrequencyItem(e.getKey(), e.getValue(), "red")));

        List<BallFrequencyItem> blueFrequency = new ArrayList<>();
        blueCount.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> blueFrequency.add(new BallFrequencyItem(e.getKey(), e.getValue(), "blue")));

        return new AnalyticsSummary(redFrequency, blueFrequency, history.size());
    }
}
