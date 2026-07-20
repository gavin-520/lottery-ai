package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Sprint 10-D: lightweight metrics summary attached to registry/status views. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dModelMetricsSummary {
    private int evaluatedPeriods;
    private double top10HitRate;
    private double top20HitRate;
    private double top50HitRate;
    private double improvementVsRandom;
    private double improvementVsFrequency;
    private LocalDateTime lastEvaluatedTime;
}
