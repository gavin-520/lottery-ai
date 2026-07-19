package com.lottery.rule.impl;

import com.lottery.entity.LotteryHistory;
import com.lottery.rule.Rule;
import com.lottery.util.LotteryBallUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MissingRule implements Rule {

    @Override
    public String name() {
        return "遗漏值";
    }

    @Override
    public double weight() {
        return 0.25;
    }

    @Override
    public void score(List<LotteryHistory> history, Map<Integer, Double> redScores, Map<Integer, Double> blueScores) {
        for (int ball = 1; ball <= 33; ball++) {
            redScores.put(ball, (double) missCount(history, ball, true));
        }
        for (int ball = 1; ball <= 16; ball++) {
            blueScores.put(ball, (double) missCount(history, ball, false));
        }
    }

    private int missCount(List<LotteryHistory> history, int ball, boolean isRed) {
        int miss = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            LotteryHistory record = history.get(i);
            if (isRed) {
                if (LotteryBallUtils.parseRedBalls(record).contains(ball)) {
                    break;
                }
            } else if (LotteryBallUtils.parseBlueBall(record) == ball) {
                break;
            }
            miss++;
        }
        return miss;
    }
}
