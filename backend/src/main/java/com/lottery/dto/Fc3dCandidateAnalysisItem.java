package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dCandidateAnalysisItem {

    private String number;
    private int score;
    private List<String> alignedSignals = new ArrayList<>();
    private List<String> riskFlags = new ArrayList<>();
    private String comment;
}
