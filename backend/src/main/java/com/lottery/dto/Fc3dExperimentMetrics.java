package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Sprint 10-C: walk-forward metrics produced by a single parameter experiment run. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dExperimentMetrics {
    private int evaluatedPeriods;
    private double top10HitRate;
    private double top20HitRate;
    private double top50HitRate;
}
