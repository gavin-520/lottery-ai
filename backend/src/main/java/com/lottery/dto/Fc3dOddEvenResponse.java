package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dOddEvenResponse {

    private String lotteryType = "FC3D";
    /** Based on latest draw: count of odd digits among the three */
    private int oddCount;
    /** Based on latest draw: count of even digits among the three */
    private int evenCount;
    /** Latest draw pattern, e.g. OOE */
    private String pattern;
    private String issue;
}
