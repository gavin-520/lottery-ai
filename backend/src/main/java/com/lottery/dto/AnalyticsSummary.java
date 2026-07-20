package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSummary {

    private List<BallFrequencyItem> redFrequency = new ArrayList<>();
    private List<BallFrequencyItem> blueFrequency = new ArrayList<>();
    private int totalPeriods;
}
