package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Walk-forward statistical backtest result for the FC3D candidate engine.
 * Evaluates the statistical model only — does not generate any new numbers.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dBacktestResult {

    private String lotteryType = "FC3D";
    private int totalPeriods;
    private int minHistory;
    private int topN;

    private double top1HitRate;
    private double top3HitRate;
    private double top5HitRate;

    /** Sprint 10-B: hit rate of the Top-N combination pool (Fc3dCombinationGenerator). */
    private double top10HitRate;
    private double top20HitRate;
    private double top50HitRate;

    private double sumAccuracy;
    private double oddEvenAccuracy;

    /** position ("hundreds"|"tens"|"units") -> accuracy of the top1 candidate digit */
    private Map<String, Double> positionAccuracy = new LinkedHashMap<>();

    private String note;
}
