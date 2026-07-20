package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sprint 11-D: one ranked entry in a period's predicted Top-N — always a number already
 * produced by {@code Fc3dCombinationGenerator}, never invented by the detail report itself.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dBacktestCandidate {
    private String number;
    /** 1-based rank within the predicted Top-N for that period. */
    private int rank;
    /** Statistical score 0-100 from the combination generator. */
    private int score;
}
