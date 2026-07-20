package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * FC3D statistical explanation of existing candidates.
 * This is analysis/explanation only — it does NOT generate new numbers.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dAnalyzeResponse {

    private String lotteryType = "FC3D";
    private Fc3dFeatureSummary features;
    private List<Fc3dCandidateAnalysisItem> candidateAnalysis = new ArrayList<>();
    private Fc3dRecommendation recommendation;
    private Double confidence;
    private String modelName;
    /** Sprint 10-B: summary of the Top50 combination pool. Additive field, nullable for legacy callers. */
    private Fc3dCandidateCoverage candidateCoverage;
}
