package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/** Sprint 11-B §4: request body for {@code POST /api/v1/fc3d/ensemble/apply-weights} — always an explicit, human-confirmed action. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dEnsembleWeightApplyRequest {
    private List<String> modelVersions;
    private Map<String, Double> weights;
}
