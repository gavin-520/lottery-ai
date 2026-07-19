package com.lottery.rule.impl;

import com.lottery.entity.LotteryHistory;
import com.lottery.rule.Rule;
import com.lottery.util.LotteryBallUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class HotColdRule implements Rule {

    @Override
    public String name() {
        return "冷热号";
    }

    @Override
    public double weight() {
        return 0.3;
    }

    @Override
    public void score(List<LotteryHistory> history, Map<Integer, Double> redScores, Map<Integer, Double> blueScores) {
        for (LotteryHistory record : history) {
            for (Integer ball : LotteryBallUtils.parseRedBalls(record)) {
                redScores.merge(ball, 1.0, Double::sum);
            }
            blueScores.merge(LotteryBallUtils.parseBlueBall(record), 1.0, Double::sum);
        }
    }
}
