package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Sprint 11-A §3: one fused Top50 candidate — number is always one already proposed by a member model. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dEnsembleCandidate {
    private String number;
    /** 0-100, relative to the strongest fused candidate in this ensemble run. */
    private int ensembleScore;
    /** How many member models proposed this number. */
    private int voteCount;
    private List<String> sourceModels = new ArrayList<>();
    private List<String> reasons = new ArrayList<>();
}
