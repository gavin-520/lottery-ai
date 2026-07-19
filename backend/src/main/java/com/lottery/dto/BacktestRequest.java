package com.lottery.dto;

import lombok.Data;

@Data
public class BacktestRequest {

    private String name = "default";
    private int minHistory = 30;
    private int topRed = 12;
}
