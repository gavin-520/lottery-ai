package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BallFrequencyItem {

    private int ball;
    private int count;
    private String type;
}
