package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sprint 11-C §1: overall walk-forward Top10/20/50 hit rate over the FULL evaluated window —
 * fractions in [0,1], e.g. {@code 0.49} means "49%".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dEnsembleReportCoverage {
    private double top10;
    private double top20;
    private double top50;
}
