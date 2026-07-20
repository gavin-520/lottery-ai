package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Sprint 10-E §3: a single named health-check result within {@link Fc3dModelHealthResponse}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dModelHealthCheck {
    private String name;
    /** Optional numeric value backing the check (e.g. the top50 hit rate); null when not applicable. */
    private Double value;
    /** GOOD | WARNING | FAILED */
    private String status;
    /** Short human-readable explanation, e.g. "已 45 天未评估". */
    private String message;
}
