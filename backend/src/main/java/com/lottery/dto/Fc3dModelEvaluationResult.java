package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sprint 10-C: walk-forward evaluation result for a single FC3D scoring model
 * (current statistical model, random baseline, or frequency-only baseline).
 * Statistical evaluation only — never a guarantee of any outcome.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dModelEvaluationResult {

    /** "fc3d-combination-model" | "random-baseline" | "frequency-only-baseline" */
    private String modelName;
    private int evaluatedPeriods;

    private double top10HitRate;
    private double top20HitRate;
    private double top50HitRate;

    /** Relative improvement of top50HitRate vs. the random baseline (e.g. 0.5 = +50%). */
    private double improvementVsRandom;
    /** Relative improvement of top50HitRate vs. the frequency-only baseline. */
    private double improvementVsFrequency;
}
