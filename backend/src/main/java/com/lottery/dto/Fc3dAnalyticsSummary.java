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
public class Fc3dAnalyticsSummary {

    private String lotteryType = "FC3D";
    private int totalPeriods;
    /** Overall digit frequency 0-9 */
    private List<BallFrequencyItem> digitFrequency = new ArrayList<>();
    private List<BallFrequencyItem> pos1Frequency = new ArrayList<>();
    private List<BallFrequencyItem> pos2Frequency = new ArrayList<>();
    private List<BallFrequencyItem> pos3Frequency = new ArrayList<>();
    /** sum_value -> count */
    private Map<Integer, Integer> sumDistribution = new LinkedHashMap<>();
    /** span_value -> count */
    private Map<Integer, Integer> spanDistribution = new LinkedHashMap<>();
    /** odd_even_pattern -> count */
    private Map<String, Integer> oddEvenDistribution = new LinkedHashMap<>();
}
