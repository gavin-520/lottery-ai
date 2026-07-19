package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PredictResponse {

    private String period;
    private List<Integer> redBalls;
    private Integer blueBall;
    private String modelName;
    private Double confidence;
    private String source;
}
