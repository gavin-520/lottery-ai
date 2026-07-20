package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sprint 10-B: summary of the Top50 combination pool, attached to
 * {@link Fc3dAnalyzeResponse} as an additive field.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dCandidateCoverage {
    private int total;
    private Fc3dScoreRange scoreRange;
    /** riskLevel ("LOW"|"MEDIUM"|"HIGH") -> candidate count */
    private Map<String, Integer> riskDistribution = new LinkedHashMap<>();
}
