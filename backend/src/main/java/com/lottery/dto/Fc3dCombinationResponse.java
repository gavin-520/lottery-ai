package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 10-B: Top-N ranked combination pool, scored purely from existing
 * statistical signals (position frequency / missing / sum / odd-even / span /
 * recent-repeat risk). Never generates or guarantees any winning number.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dCombinationResponse {

    private String lotteryType = "FC3D";
    private String modelVersion;
    private int totalCandidates;
    private List<Fc3dCombinationCandidate> candidates = new ArrayList<>();
}
