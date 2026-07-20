package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 11-A §6: walk-forward comparison of the fused ensemble Top10/20/50 hit rates against
 * the single production model's own Top10/20/50 hit rates, over the identical evaluation window.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dEnsembleBacktestResult {
    private List<String> modelVersions = new ArrayList<>();
    /** The single model used as the baseline for comparison — normally the production model. */
    private String singleModelVersion;
    private int evaluatedPeriods;
    private double singleModelTop10HitRate;
    private double singleModelTop20HitRate;
    private double singleModelTop50HitRate;
    private double ensembleTop10HitRate;
    private double ensembleTop20HitRate;
    private double ensembleTop50HitRate;
    /** {@code ensembleTop50HitRate - singleModelTop50HitRate}. */
    private double improvement;
}
