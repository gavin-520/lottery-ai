package com.lottery.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for the FC3D statistical candidate model (Fc3dRuleEngine).
 * Field defaults intentionally mirror the previously hardcoded constants so that
 * a plain {@code new Fc3dModelConfig()} (no Spring context, e.g. in tests) yields
 * IDENTICAL candidate scoring to the pre-Sprint-10-A behavior.
 *
 * <p>This does not change candidate generation logic — only makes its weights
 * and model identity externally configurable via {@code application.yml}
 * (prefix {@code lottery.fc3d.model}).</p>
 */
@Data
@Component
public class Fc3dModelConfig {

    @Value("${lottery.fc3d.model.name:fc3d-statistical-model}")
    private String name = "fc3d-statistical-model";

    @Value("${lottery.fc3d.model.version:v2}")
    private String version = "v2";

    /** Weight of position-frequency signal (default matches legacy hardcoded 0.35). */
    @Value("${lottery.fc3d.model.weight.frequency:0.35}")
    private double weightFrequency = 0.35;

    /** Weight of missing-value signal (default matches legacy hardcoded 0.20). */
    @Value("${lottery.fc3d.model.weight.missing:0.20}")
    private double weightMissing = 0.20;

    /** Weight of sum-distribution signal (default matches legacy hardcoded 0.25). */
    @Value("${lottery.fc3d.model.weight.sum:0.25}")
    private double weightSum = 0.25;

    /** Weight of odd/even structure signal (default matches legacy hardcoded 0.15). */
    @Value("${lottery.fc3d.model.weight.odd-even:0.15}")
    private double weightOddEven = 0.15;

    /** Bonus applied when a candidate does NOT repeat a recent draw (default 0.05). */
    @Value("${lottery.fc3d.model.weight.anti-repeat-bonus:0.05}")
    private double weightAntiRepeatBonus = 0.05;

    /** Penalty multiplier applied when a candidate repeats a recent draw (default 0.35). */
    @Value("${lottery.fc3d.model.weight.anti-repeat-penalty:0.35}")
    private double weightAntiRepeatPenalty = 0.35;

    /** Candidate digit-pool size per position (default matches legacy hardcoded 4). */
    @Value("${lottery.fc3d.model.pool-per-position:4}")
    private int poolPerPosition = 4;

    /** How many recent periods count as "recent" for the anti-repeat signal (default 5). */
    @Value("${lottery.fc3d.model.recent-avoid-periods:5}")
    private int recentAvoidPeriods = 5;

    /**
     * Weight of span (max-min digit) signal — used only by {@link com.lottery.rule.fc3d.Fc3dCombinationGenerator}
     * (Sprint 10-B). {@link com.lottery.rule.fc3d.Fc3dRuleEngine} does not use this field.
     */
    @Value("${lottery.fc3d.model.weight.span:0.10}")
    private double weightSpan = 0.10;

    /** Sprint 10-B: how many top-scored combinations to return from the full pool (default 50). */
    @Value("${lottery.fc3d.model.combination.candidate-count:50}")
    private int candidateCount = 50;

    /** Sprint 10-B: size of the scored combination space, capped at the full FC3D domain of 1000 (default 1000). */
    @Value("${lottery.fc3d.model.combination.max-pool-size:1000}")
    private int maxPoolSize = 1000;

    /**
     * Sprint 10-B: model version tag reported by {@link com.lottery.rule.fc3d.Fc3dCombinationGenerator} output.
     * Kept independent from {@link #version} so the Sprint 10-A default-compatibility guarantee
     * for {@code Fc3dPredictResponse.modelVersion} ("v2") is never affected.
     */
    @Value("${lottery.fc3d.model.combination.version:v3}")
    private String combinationVersion = "v3";

    /**
     * Sprint 10-C: master switch for the batch parameter-experiment endpoint
     * ({@code POST /api/v1/fc3d/model-evaluation/experiments}). Disabled by default since
     * each experiment re-runs a full walk-forward backtest and can be resource-intensive.
     */
    @Value("${lottery.fc3d.model.experiment.enabled:false}")
    private boolean experimentEnabled = false;

    /** Sprint 10-C: max number of weight-parameter sets accepted per experiment batch request. */
    @Value("${lottery.fc3d.model.experiment.max-combinations:100}")
    private int experimentMaxCombinations = 100;

    /** {@code name-version}, e.g. {@code fc3d-statistical-model-v2}. */
    public String getFullModelName() {
        return name + "-" + version;
    }

    /** Sprint 10-D: current weights as a plain map — used by the model registry/metric persistence. */
    public Map<String, Double> toWeightMap() {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("frequency", weightFrequency);
        map.put("missing", weightMissing);
        map.put("sum", weightSum);
        map.put("oddEven", weightOddEven);
        map.put("span", weightSpan);
        return map;
    }
}
