package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Sprint 10-E §3: {@code GET /api/v1/fc3d/model/health} response. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dModelHealthResponse {
    /** Overall status — the worst of all individual checks: GOOD | WARNING | FAILED. */
    private String status;
    /** The production model this health snapshot reflects, or null if none exists. */
    private String model;
    private List<Fc3dModelHealthCheck> checks;
}
