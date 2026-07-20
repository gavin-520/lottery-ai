package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 11-B §3: {@code POST /api/v1/fc3d/ensemble/optimize} response. Advisory only — never
 * applies {@code bestWeights} automatically; a separate, explicit
 * {@code POST /ensemble/apply-weights} call (human-confirmed in the UI) is required.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dEnsembleOptimizationResponse {
    private List<String> modelVersions = new ArrayList<>();
    /** Weights currently in effect (registry role-based default, or a previously applied override). */
    private Map<String, Double> currentWeights = new LinkedHashMap<>();
    /** Grid-search recommended weights — guaranteed to score >= currentWeights on Top50. */
    private Map<String, Double> bestWeights = new LinkedHashMap<>();
    private Fc3dEnsembleOptimizationMetrics before;
    private Fc3dEnsembleOptimizationMetrics after;
    /** {@code after.top50 - before.top50} */
    private double improvement;
    private int evaluatedPeriods;
    /** "grid-search" — the only search strategy implemented so far. */
    private String searchMethod;
    private LocalDateTime createdTime;
}
