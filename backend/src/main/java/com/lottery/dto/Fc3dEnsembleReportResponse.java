package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 11-C §1: {@code GET /api/v1/fc3d/ensemble/report} response — a read-only, human-facing
 * summary of the ensemble's walk-forward performance. Never generates numbers, never influences
 * scoring or weights; purely a report over data already produced by the ensemble fusion engine's
 * evaluations (same contract as {@code Fc3dEnsembleBacktestService} / {@code
 * Fc3dEnsembleWeightOptimizer}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dEnsembleReportResponse {
    /** Human-friendly label, e.g. {@code "ensemble-v3"} (single member) or {@code "ensemble(v3+v3-exp-001)"}. */
    private String ensembleLabel;
    private List<String> modelVersions = new ArrayList<>();
    private int evaluatedPeriods;
    private Fc3dEnsembleReportCoverage coverage;
    private Fc3dEnsembleReportStability stability;
    private List<Fc3dEnsembleReportTimeWindow> timeWindows = new ArrayList<>();
    /** Overall status — the worst of {@code healthChecks}: GOOD | WARNING | FAILED. */
    private String health;
    private List<Fc3dModelHealthCheck> healthChecks = new ArrayList<>();
    private LocalDateTime createdTime;
}
