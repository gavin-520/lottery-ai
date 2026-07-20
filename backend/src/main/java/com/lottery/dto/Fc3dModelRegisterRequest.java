package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/** Sprint 10-D §2: request body for {@code POST /api/v1/fc3d/model/register}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dModelRegisterRequest {
    private String modelVersion;
    private Map<String, Double> parameters = new LinkedHashMap<>();
}
