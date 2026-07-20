package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dCandidate {

    /** Three-digit string, e.g. "123" */
    private String number;
    /** Statistical score 0–100 (not a win guarantee) */
    private int score;
    private List<String> reasons = new ArrayList<>();
}
