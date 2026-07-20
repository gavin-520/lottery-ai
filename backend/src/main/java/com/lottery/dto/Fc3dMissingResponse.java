package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dMissingResponse {

    private String lotteryType = "FC3D";
    private int totalPeriods;
    private List<Fc3dMissingItem> items = new ArrayList<>();
}
