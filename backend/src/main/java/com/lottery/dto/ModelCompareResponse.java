package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ModelCompareResponse {

    private List<ModelPredictionItem> models;
}
