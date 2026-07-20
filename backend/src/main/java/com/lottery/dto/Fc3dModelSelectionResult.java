package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Sprint 10-D: recommendation output of {@code Fc3dModelSelector}. Advisory only — never auto-applied. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dModelSelectionResult {
    /** Best-ranked registered + active model version, or {@code null} if no eligible candidate exists. */
    private String selectedModel;
    private List<String> reason = new ArrayList<>();
}
