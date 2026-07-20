package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sprint 10-D: a registered FC3D model version, as returned by {@code Fc3dModelRegistryService}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dModelInfo {
    private String version;
    /** "ACTIVE" (enabled / eligible) | "INACTIVE" (disabled, excluded from auto-selection) */
    private String status;
    private LocalDateTime createdTime;
    private Map<String, Double> parameters = new LinkedHashMap<>();
    /** Latest recorded walk-forward metrics for this version, if any evaluation has been persisted. */
    private Fc3dModelMetricsSummary metrics;
    /** Whether this version is currently the one Predict actually serves. */
    private boolean production;
}
