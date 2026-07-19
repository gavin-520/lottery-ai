package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class OpsOverviewResponse {

    private List<RegionSlaSummary> regions;
    private long breaches24h;
    private long failedSyncs24h;
    private boolean cacheEnabled;
}
