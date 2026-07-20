package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dRecommendation {

    private String preferred;
    private List<String> rationale = new ArrayList<>();
    /** Statistical-only disclaimer; never omit. */
    private String disclaimer;
}
