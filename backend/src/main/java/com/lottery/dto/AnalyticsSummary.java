package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AnalyticsSummary {

    private List<BallFrequencyItem> redFrequency;
    private List<BallFrequencyItem> blueFrequency;
    private int totalPeriods;
}
