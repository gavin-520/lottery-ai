package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 11-D: one walk-forward period's detail — train = {@code history[0:i]}, predict for
 * period {@code i}, actual = {@code history[i]}. Never uses future data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dBacktestDetailItem {
    private String issue;
    /** Size of the train slice used for this period ({@code i}). */
    private int trainPeriods;
    private String modelVersion;
    private List<Fc3dBacktestCandidate> predictedTop50 = new ArrayList<>();
    private String actualNumber;
    private boolean hit;
    /** 1-based rank of the actual number in predictedTop50; {@code null} when miss. */
    private Integer hitRank;
    /** Score of the hitting candidate; {@code null} when miss. */
    private Integer hitScore;
    /** {@code TOP1}/{@code TOP10}/{@code TOP20}/{@code TOP50}/{@code MISS}. */
    private String hitLevel;
}
