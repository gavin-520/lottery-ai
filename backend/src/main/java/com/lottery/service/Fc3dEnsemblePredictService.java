package com.lottery.service;

import com.lottery.config.Fc3dModelConfig;
import com.lottery.dto.Fc3dCombinationResponse;
import com.lottery.dto.Fc3dEnsembleMemberInput;
import com.lottery.dto.Fc3dEnsembleResponse;
import com.lottery.dto.Fc3dFrequencyResponse;
import com.lottery.dto.Fc3dMissingResponse;
import com.lottery.dto.Fc3dModelInfo;
import com.lottery.dto.Fc3dSumAnalysisResponse;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.rule.fc3d.Fc3dCombinationGenerator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Sprint 11-A §1/§4: orchestrates the live {@code GET /api/v1/fc3d/predict/ensemble} flow —
 * resolves which registered models participate, resolves their {@code Fc3dModelRegistry}-driven
 * fusion weights, builds each model's OWN already-computed Top-N candidate list (never new
 * numbers), then delegates to {@link Fc3dEnsembleEngine} for weighted-voting fusion.
 */
@Service
public class Fc3dEnsemblePredictService {

    /** Sprint 11-A §2 example weights: production / experiment / baseline tiers. */
    public static final double PRODUCTION_WEIGHT = 0.5;
    public static final double EXPERIMENT_WEIGHT = 0.3;
    public static final double BASELINE_WEIGHT = 0.2;

    public static final int DEFAULT_TOP_N = 50;

    private final Fc3dPredictService fc3dPredictService;
    private final Fc3dAnalyticsService fc3dAnalyticsService;
    private final Fc3dModelRegistryService fc3dModelRegistryService;
    private final Fc3dEnsembleEngine fc3dEnsembleEngine;
    private final Fc3dEnsembleWeightOverrideService fc3dEnsembleWeightOverrideService;

    public Fc3dEnsemblePredictService(Fc3dPredictService fc3dPredictService,
                                      Fc3dAnalyticsService fc3dAnalyticsService,
                                      Fc3dModelRegistryService fc3dModelRegistryService,
                                      Fc3dEnsembleEngine fc3dEnsembleEngine,
                                      Fc3dEnsembleWeightOverrideService fc3dEnsembleWeightOverrideService) {
        this.fc3dPredictService = fc3dPredictService;
        this.fc3dAnalyticsService = fc3dAnalyticsService;
        this.fc3dModelRegistryService = fc3dModelRegistryService;
        this.fc3dEnsembleEngine = fc3dEnsembleEngine;
        this.fc3dEnsembleWeightOverrideService = fc3dEnsembleWeightOverrideService;
    }

    /** Sprint 11-A §4: {@code GET /predict/ensemble}. {@code modelVersions} optional — defaults to every currently ACTIVE registered model. */
    public Fc3dEnsembleResponse predictEnsemble(List<String> modelVersions, Integer topN) {
        List<Fc3dDrawEntity> history = fc3dPredictService.listHistoryAsc();
        List<String> versions = resolveVersions(modelVersions);
        Map<String, Double> weights = resolveWeights(versions);
        List<Fc3dEnsembleMemberInput> members = buildMembers(history, versions);
        int limit = topN != null && topN > 0 ? topN : DEFAULT_TOP_N;
        return fc3dEnsembleEngine.fuse(members, weights, limit);
    }

    /** Explicit versions win as-is (manual override); otherwise every ACTIVE registered version participates. */
    public List<String> resolveVersions(List<String> requested) {
        if (requested != null && !requested.isEmpty()) {
            List<String> cleaned = new ArrayList<>();
            for (String v : requested) {
                if (v != null && !v.isBlank() && !cleaned.contains(v)) {
                    cleaned.add(v);
                }
            }
            if (!cleaned.isEmpty()) {
                return cleaned;
            }
        }
        List<String> active = new ArrayList<>();
        for (Fc3dModelInfo info : fc3dModelRegistryService.listModels()) {
            if (Fc3dModelRegistryService.STATUS_ACTIVE.equals(info.getStatus())) {
                active.add(info.getVersion());
            }
        }
        return active;
    }

    /**
     * Sprint 11-A §2 / 11-B §4: {@code modelWeight} source is {@code Fc3dModelRegistry} by
     * default — the current production model gets the "production" tier weight (0.5), a version
     * whose name marks it as a frequency/baseline model gets the "baseline" tier (0.2), everything
     * else gets the "experiment" tier (0.3). If a human has explicitly applied a
     * {@link Fc3dEnsembleWeightOptimizer} recommendation for EXACTLY this set of versions (via
     * {@code POST /ensemble/apply-weights}), that override wins instead. Always normalized so the
     * participating subset's weights sum to 1, regardless of how many models are fused.
     */
    public Map<String, Double> resolveWeights(List<String> versions) {
        Optional<Map<String, Double>> override = fc3dEnsembleWeightOverrideService.resolve(versions);
        if (override.isPresent()) {
            return Fc3dEnsembleEngine.normalizeWeights(override.get());
        }
        Map<String, Double> raw = new LinkedHashMap<>();
        for (String version : versions) {
            raw.put(version, roleWeight(version));
        }
        return Fc3dEnsembleEngine.normalizeWeights(raw);
    }

    /** Builds each participating model's OWN Top-N candidate list from the SAME history slice — no new numbers, no cross-model leakage. */
    public List<Fc3dEnsembleMemberInput> buildMembers(List<Fc3dDrawEntity> history, List<String> versions) {
        Fc3dFrequencyResponse frequency = fc3dAnalyticsService.getPositionFrequency(history);
        Fc3dMissingResponse missing = fc3dAnalyticsService.calculateMissing(history);
        Fc3dSumAnalysisResponse sumAnalysis = fc3dAnalyticsService.calculateSumAnalysis(history, 30);

        List<Fc3dEnsembleMemberInput> members = new ArrayList<>(versions.size());
        for (String version : versions) {
            Fc3dModelConfig config = fc3dModelRegistryService.resolveConfig(version);
            Fc3dCombinationGenerator generator = new Fc3dCombinationGenerator(config);
            Fc3dCombinationResponse response = generator.generate(history, frequency, missing, sumAnalysis);
            members.add(new Fc3dEnsembleMemberInput(version, response.getCandidates()));
        }
        return members;
    }

    private double roleWeight(String version) {
        Optional<Fc3dModelInfo> info = fc3dModelRegistryService.get(version);
        boolean production = info.map(Fc3dModelInfo::isProduction).orElse(false);
        if (production) {
            return PRODUCTION_WEIGHT;
        }
        if (isBaselineVersion(version)) {
            return BASELINE_WEIGHT;
        }
        return EXPERIMENT_WEIGHT;
    }

    private boolean isBaselineVersion(String version) {
        String lower = version.toLowerCase(Locale.ROOT);
        return lower.contains("baseline") || lower.contains("freq");
    }
}
