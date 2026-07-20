package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 11-D: {@code GET /api/v1/fc3d/backtest/details} response — per-period walk-forward
 * Top-N detail plus aggregate hit statistics. Evaluation only; never generates new numbers.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dBacktestDetailResponse {
    private String lotteryType;
    /** Resolved model version actually used for every period in {@code details}. */
    private String modelVersion;
    private int evaluatedPeriods;
    private int topN;
    private int hitCount;
    private double hitRate;
    /** Mean of {@code hitRank} over hit periods only; 0 when there are no hits. */
    private double averageHitRank;
    private int longestMissStreak;
    private List<Fc3dBacktestDetailItem> details = new ArrayList<>();
}
