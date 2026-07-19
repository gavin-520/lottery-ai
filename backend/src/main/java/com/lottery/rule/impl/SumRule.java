package com.lottery.rule.impl;

import com.lottery.entity.LotteryHistory;
import com.lottery.rule.Rule;
import com.lottery.util.LotteryBallUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SumRule implements Rule {

    @Override
    public String name() {
        return "和值";
    }

    @Override
    public double weight() {
        return 0.2;
    }

    @Override
    public void score(List<LotteryHistory> history, Map<Integer, Double> redScores, Map<Integer, Double> blueScores) {
        Map<Integer, Integer> sumFreq = new HashMap<>();
        for (LotteryHistory record : history) {
            sumFreq.merge(LotteryBallUtils.sumRedBalls(record), 1, Integer::sum);
        }
        for (int ball = 1; ball <= 33; ball++) {
            double score = 0;
            for (Map.Entry<Integer, Integer> entry : sumFreq.entrySet()) {
                if (entry.getKey() >= ball) {
                    score += entry.getValue();
                }
            }
            redScores.put(ball, score);
        }
    }
}
