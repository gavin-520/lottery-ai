package com.lottery.rule;

import com.lottery.entity.LotteryHistory;

import java.util.List;
import java.util.Map;

public interface Rule {

    String name();

    double weight();

    void score(List<LotteryHistory> history, Map<Integer, Double> redScores, Map<Integer, Double> blueScores);
}
