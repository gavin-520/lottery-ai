package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dBacktestResponse {

    private String lotteryType;
    private int totalPeriods;
    private double exactHitRate;
    private double digitHitRate;
    private double avgDigitHits;
    private int maxDigitHits;
    private String note;
}
