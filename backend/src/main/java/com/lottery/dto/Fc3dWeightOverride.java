package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sprint 10-C: a single weight-parameter set to test in a batch experiment run.
 * Any field left {@code null} falls back to the current {@code Fc3dModelConfig} default,
 * so callers can override only the weights they want to test.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dWeightOverride {
    private Double frequency;
    private Double missing;
    private Double sum;
    private Double oddEven;
    private Double span;
}
