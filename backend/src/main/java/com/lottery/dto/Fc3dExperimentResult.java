package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sprint 10-C: result of a single parameter-optimization experiment
 * (one weight-parameter set, evaluated via walk-forward backtest).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dExperimentResult {

    /** e.g. "experiment-001" */
    private String experimentId;
    /** Combination-model version tag used for this experiment run, e.g. "v3-experiment". */
    private String modelVersion;
    /** Effective weights used for this run (frequency/missing/sum/oddEven/span). */
    private Map<String, Double> parameters = new LinkedHashMap<>();
    private Fc3dExperimentMetrics metrics;
    private LocalDateTime createdTime;
}
