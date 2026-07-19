package com.lottery.rule.impl;

import com.lottery.entity.LotteryHistory;
import com.lottery.rule.Rule;
import com.lottery.util.LotteryBallUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SpanRule implements Rule {

    @Override
    public String name() {
        return "跨度";
    }

    @Override
    public double weight() {
        return 0.15;
    }

    @Override
    public void score(List<LotteryHistory> history, Map<Integer, Double> redScores, Map<Integer, Double> blueScores) {
        Map<Integer, Integer> spanFreq = new HashMap<>();
        for (LotteryHistory record : history) {
            spanFreq.merge(LotteryBallUtils.spanRedBalls(record), 1, Integer::sum);
        }
        for (int ball = 1; ball <= 33; ball++) {
            double score = 0;
            for (Map.Entry<Integer, Integer> entry : spanFreq.entrySet()) {
                if (entry.getKey() >= Math.min(ball, 33 - ball)) {
                    score += entry.getValue();
                }
            }
            redScores.put(ball, score);
        }
    }
}
