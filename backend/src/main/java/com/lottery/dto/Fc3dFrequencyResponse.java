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
public class Fc3dFrequencyResponse {

    private String lotteryType = "FC3D";
    private int totalPeriods;

    /** 百位 0-9 → count */
    private Map<String, Integer> hundreds = new LinkedHashMap<>();
    /** 十位 0-9 → count */
    private Map<String, Integer> tens = new LinkedHashMap<>();
    /** 个位 0-9 → count */
    private Map<String, Integer> units = new LinkedHashMap<>();

    // ---- backward-compatible fields for existing AnalyticsView ----
    private List<BallFrequencyItem> digitFrequency = new ArrayList<>();
    private List<BallFrequencyItem> pos1Frequency = new ArrayList<>();
    private List<BallFrequencyItem> pos2Frequency = new ArrayList<>();
    private List<BallFrequencyItem> pos3Frequency = new ArrayList<>();
    private Map<Integer, Integer> sumDistribution = new LinkedHashMap<>();
    private Map<Integer, Integer> spanDistribution = new LinkedHashMap<>();
    private Map<String, Integer> oddEvenDistribution = new LinkedHashMap<>();
}
