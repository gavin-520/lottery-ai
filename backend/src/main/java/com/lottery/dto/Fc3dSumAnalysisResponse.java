package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dSumAnalysisResponse {

    private String lotteryType = "FC3D";
    private int totalPeriods;
    private double average;
    private Map<String, Integer> distribution = new LinkedHashMap<>();
    private List<Fc3dSumTrendItem> recentTrend = new ArrayList<>();
}
