package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ModelPredictionItem {

    private String modelName;
    private List<Integer> redBalls;
    private Integer blueBall;
    private Double confidence;
}
