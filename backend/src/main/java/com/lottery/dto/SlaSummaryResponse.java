package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SlaSummaryResponse {

    private long totalCalls;
    private long successCalls;
    private long failedCalls;
    private double successRate;
    private double avgLatencyMs;
    private double p95LatencyMs;
    private String region;
}
