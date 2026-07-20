package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dFeatureSummary {

    /** position ("hundreds"|"tens"|"units") -> top hot digits */
    private Map<String, List<Integer>> hotDigits = new LinkedHashMap<>();
    private Double sumAverage;
    private String dominantOddEven;
    private List<String> notes = new ArrayList<>();
}
