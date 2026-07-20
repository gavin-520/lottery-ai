package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Sprint 10-D §5: {@code GET /api/v1/fc3d/model/status} response. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dModelStatusResponse {
    private String activeModel;
    /** ISO date/time of the most recent persisted evaluation for the active model, or null if never evaluated. */
    private String lastEvaluation;
    private Fc3dModelMetricsSummary metrics;
    /** GOOD | WARN | POOR | UNKNOWN (no evaluation history yet). */
    private String health;
}
