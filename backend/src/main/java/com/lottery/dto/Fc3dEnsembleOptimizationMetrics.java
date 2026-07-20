package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Sprint 11-B §1/§3: walk-forward Top10/20/50 hit rates for one specific ensemble weight assignment. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dEnsembleOptimizationMetrics {
    private double top10;
    private double top20;
    private double top50;
}
