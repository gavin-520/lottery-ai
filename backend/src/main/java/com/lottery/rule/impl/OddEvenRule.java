package com.lottery.rule.impl;

import com.lottery.entity.LotteryHistory;
import com.lottery.rule.Rule;
import com.lottery.util.LotteryBallUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OddEvenRule implements Rule {

    @Override
    public String name() {
        return "奇偶比";
    }

    @Override
    public double weight() {
        return 0.1;
    }

    @Override
    public void score(List<LotteryHistory> history, Map<Integer, Double> redScores, Map<Integer, Double> blueScores) {
        Map<Integer, Integer> oddCountFreq = new HashMap<>();
        for (LotteryHistory record : history) {
            oddCountFreq.merge(LotteryBallUtils.oddRedCount(record), 1, Integer::sum);
        }
        int preferredOdd = oddCountFreq.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(3);

        for (int ball = 1; ball <= 33; ball++) {
            boolean isOdd = ball % 2 == 1;
            boolean preferOdd = preferredOdd >= 3;
            redScores.put(ball, (isOdd == preferOdd) ? 1.0 : 0.0);
        }
    }
}
