package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class BacktestResponse {

    private Long reportId;
    private String name;
    private int totalPeriods;
    private int totalRedHits;
    private int maxRedHits;
    private double avgRedHits;
    private double redHitRate;
    private double blueHitRate;
    private Map<String, Object> summary;
}
