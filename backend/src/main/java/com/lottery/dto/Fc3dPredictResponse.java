package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dPredictResponse {

    private String lotteryType;
    private String issue;
    private Integer digit1;
    private Integer digit2;
    private Integer digit3;
    private Integer sumValue;
    private Integer spanValue;
    private String oddEvenPattern;
    private String modelName;
    /** Statistical model version (Sprint 10-A), e.g. "v2". Additive field — never omitted, defaults to null for legacy paths that don't set it. */
    private String modelVersion;
    private Double confidence;
    private String source;

    /** Ranked statistical candidates (explainable). */
    private List<Fc3dCandidate> candidates = new ArrayList<>();
    /** Best candidate number string, e.g. "123". */
    private String best;
}
