package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 11-A §1: one member model's ALREADY-COMPUTED Top-N candidates, fed into
 * {@code Fc3dEnsembleEngine}. The engine never re-generates numbers — it only fuses the
 * {@code candidates} lists supplied here (each produced independently by an existing
 * registered model's {@code Fc3dCombinationGenerator}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dEnsembleMemberInput {
    private String modelVersion;
    private List<Fc3dCombinationCandidate> candidates = new ArrayList<>();
}
