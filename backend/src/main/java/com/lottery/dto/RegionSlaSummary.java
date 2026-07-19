package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RegionSlaSummary {

    private String region;
    private long totalCalls;
    private double successRate;
    private double p95LatencyMs;
    private long failedSyncs;
}
