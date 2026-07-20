package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sprint 11-A §3: {@code GET /api/v1/fc3d/predict/ensemble} response. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dEnsembleResponse {
    private List<String> modelVersions = new ArrayList<>();
    /** Sprint 11-A §5: normalized (sums to 1) fusion weight actually applied to each participating model. */
    private Map<String, Double> modelWeights = new LinkedHashMap<>();
    private List<Fc3dEnsembleCandidate> topCandidates = new ArrayList<>();
    /** Sprint 11-A §2: "weighted-voting" — the only fusion strategy implemented so far. */
    private String fusionMethod;
    private LocalDateTime createdTime;
}
