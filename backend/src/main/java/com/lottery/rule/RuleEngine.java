package com.lottery.rule;

import com.lottery.entity.LotteryHistory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RuleEngine {

    private final List<Rule> rules;

    public RuleEngine(List<Rule> rules) {
        this.rules = rules;
    }

    public RuleScoreResult scoreAll(List<LotteryHistory> history) {
        Map<Integer, Double> redTotal = initRedScores();
        Map<Integer, Double> blueTotal = initBlueScores();

        for (Rule rule : rules) {
            Map<Integer, Double> redScores = initRedScores();
            Map<Integer, Double> blueScores = initBlueScores();
            rule.score(history, redScores, blueScores);
            double weight = rule.weight();
            redScores.forEach((k, v) -> redTotal.merge(k, v * weight, Double::sum));
            blueScores.forEach((k, v) -> blueTotal.merge(k, v * weight, Double::sum));
        }

        return new RuleScoreResult(redTotal, blueTotal);
    }

    private Map<Integer, Double> initRedScores() {
        Map<Integer, Double> scores = new HashMap<>();
        for (int i = 1; i <= 33; i++) {
            scores.put(i, 0.0);
        }
        return scores;
    }

    private Map<Integer, Double> initBlueScores() {
        Map<Integer, Double> scores = new HashMap<>();
        for (int i = 1; i <= 16; i++) {
            scores.put(i, 0.0);
        }
        return scores;
    }

    public record RuleScoreResult(Map<Integer, Double> redScores, Map<Integer, Double> blueScores) {}
}
