package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Sprint 10-D §6: one row of the historical model leaderboard (frontend model-center page). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dModelLeaderboardEntry {
    private int rank;
    private String version;
    private String status;
    private double top10HitRate;
    private double top20HitRate;
    private double top50HitRate;
    private double improvementVsRandom;
    private double improvementVsFrequency;
    private LocalDateTime lastEvaluatedTime;
}
