package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * A single ranked candidate from the Sprint 10-B Top50 combination pool.
 * Statistical analysis only — never a guarantee of any outcome.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dCombinationCandidate {

    /** Three-digit string, e.g. "123". */
    private String number;
    /** Statistical score 0-100 (not a win guarantee). */
    private int score;
    /** 1-based rank within the returned candidate list. */
    private int rank;
    private List<String> reasons = new ArrayList<>();
    /** "LOW" | "MEDIUM" | "HIGH" */
    private String riskLevel;
}
