package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sprint 11-C §1: stability of the Top50 hit rate ACROSS the {@code timeWindows} buckets (each
 * bucket = one calendar quarter within the evaluated window).
 *
 * <ul>
 *     <li>{@code average} — mean Top50 hit rate across buckets.</li>
 *     <li>{@code volatility} — population standard deviation of the per-bucket Top50 hit rate.</li>
 *     <li>{@code maxDrawdown} — largest peak-to-trough decline of the Top50 hit rate across
 *     buckets in chronological order (running-max minus current, at its worst point).</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dEnsembleReportStability {
    private double average;
    private double volatility;
    private double maxDrawdown;
}
