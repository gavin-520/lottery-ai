package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Sprint 11-C §1: one calendar-quarter bucket's Top50 hit rate, e.g. {@code label = "2025 Q1"}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dEnsembleReportTimeWindow {
    private String label;
    private double top50HitRate;
    private int evaluatedPeriods;
}
