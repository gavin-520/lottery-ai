package com.lottery.service;

import com.lottery.dto.Fc3dCombinationCandidate;
import com.lottery.dto.Fc3dEnsembleCandidate;
import com.lottery.dto.Fc3dEnsembleMemberInput;
import com.lottery.dto.Fc3dEnsembleResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Sprint 11-A §1/§2: fuses ALREADY-COMPUTED candidate lists from multiple registered FC3D
 * models into a single weighted Top-N ranking ("weighted voting").
 *
 * <p><b>Never generates numbers.</b> This engine has no access to draw history, analytics, or
 * any {@code Fc3dModelConfig} — it is a pure function of {@code (members, modelWeights, topN)}.
 * Every number in its output was already proposed by at least one member model; a number can
 * never appear in the fused Top-N unless it appears in at least one input candidate list. This
 * also means the engine cannot leak future data on its own: whoever builds the member candidate
 * lists (from a walk-forward train slice, or from full history for a live prediction) is solely
 * responsible for that boundary — see {@code Fc3dEnsemblePredictService} / {@code Fc3dEnsembleBacktestService}.</p>
 *
 * <p>Formula: {@code ensembleScore(number) = Σ (modelScore(number, m) × modelWeight(m))} over
 * every member {@code m} whose candidate list contains {@code number}, then re-normalized to a
 * 0-100 scale relative to the strongest fused candidate — the same "relative score" convention
 * {@link com.lottery.rule.fc3d.Fc3dCombinationGenerator} already uses.</p>
 */
@Component
public class Fc3dEnsembleEngine {

    public static final String FUSION_METHOD_WEIGHTED_VOTING = "weighted-voting";

    /** Reasons are computed from the fused-in evidence only — never from any newly invented signal. */
    private static final double STABLE_SCORE_STDDEV_THRESHOLD = 8.0;

    public Fc3dEnsembleResponse fuse(List<Fc3dEnsembleMemberInput> members, Map<String, Double> modelWeights, int topN) {
        Map<String, Double> weights = normalizeWeights(modelWeights);
        List<Fc3dEnsembleCandidate> all = fuseWithNormalizedWeights(members, weights);

        int limit = Math.max(1, topN);
        List<Fc3dEnsembleCandidate> top = all.size() > limit ? new ArrayList<>(all.subList(0, limit)) : all;

        List<String> modelVersions = new ArrayList<>();
        if (members != null) {
            for (Fc3dEnsembleMemberInput member : members) {
                if (member != null && member.getModelVersion() != null) {
                    modelVersions.add(member.getModelVersion());
                }
            }
        }

        Fc3dEnsembleResponse response = new Fc3dEnsembleResponse();
        response.setModelVersions(modelVersions);
        response.setModelWeights(weights);
        response.setTopCandidates(top);
        response.setFusionMethod(FUSION_METHOD_WEIGHTED_VOTING);
        response.setCreatedTime(LocalDateTime.now());
        return response;
    }

    /**
     * Fuses every candidate that appears in ANY member's list, fully sorted (not limited).
     * Exposed separately so callers can slice Top10/Top20/Top50 off the SAME ranking — this is
     * what guarantees Top10 ⊆ Top20 ⊆ Top50 as a structural property rather than a coincidence.
     */
    public List<Fc3dEnsembleCandidate> fuseAll(List<Fc3dEnsembleMemberInput> members, Map<String, Double> modelWeights) {
        return fuseWithNormalizedWeights(members, normalizeWeights(modelWeights));
    }

    private List<Fc3dEnsembleCandidate> fuseWithNormalizedWeights(List<Fc3dEnsembleMemberInput> members, Map<String, Double> weights) {
        if (members == null || members.isEmpty()) {
            return List.of();
        }

        Map<String, Aggregate> byNumber = new LinkedHashMap<>();
        int totalModels = 0;
        for (Fc3dEnsembleMemberInput member : members) {
            if (member == null || member.getModelVersion() == null) {
                continue;
            }
            totalModels++;
            double weight = weights.getOrDefault(member.getModelVersion(), 0.0);
            if (member.getCandidates() == null) {
                continue;
            }
            for (Fc3dCombinationCandidate candidate : member.getCandidates()) {
                if (candidate == null || candidate.getNumber() == null) {
                    continue;
                }
                Aggregate aggregate = byNumber.computeIfAbsent(candidate.getNumber(), n -> new Aggregate());
                aggregate.rawScore += candidate.getScore() * weight;
                aggregate.voteCount++;
                aggregate.sourceModels.add(member.getModelVersion());
                aggregate.perModelScores.put(member.getModelVersion(), candidate.getScore());
            }
        }

        double maxRaw = 1e-9;
        for (Aggregate aggregate : byNumber.values()) {
            maxRaw = Math.max(maxRaw, aggregate.rawScore);
        }

        List<Fc3dEnsembleCandidate> all = new ArrayList<>(byNumber.size());
        for (Map.Entry<String, Aggregate> entry : byNumber.entrySet()) {
            Aggregate aggregate = entry.getValue();
            int score = clampScore((int) Math.round(100.0 * aggregate.rawScore / maxRaw));
            all.add(new Fc3dEnsembleCandidate(entry.getKey(), score, aggregate.voteCount,
                    new ArrayList<>(aggregate.sourceModels), buildReasons(aggregate, totalModels)));
        }

        all.sort(Comparator
                .comparingInt(Fc3dEnsembleCandidate::getEnsembleScore).reversed()
                .thenComparing(Comparator.comparingInt(Fc3dEnsembleCandidate::getVoteCount).reversed())
                .thenComparing(Fc3dEnsembleCandidate::getNumber));
        return all;
    }

    /**
     * Sprint 11-A §2: weights come from {@code Fc3dModelRegistry} (via
     * {@code Fc3dEnsemblePredictService}) but may not sum to 1 (e.g. an experiment-tier default
     * of 0.3 for 3 models sums to 0.9) — normalize here so {@code ensembleScore} stays on the
     * same familiar 0-100 scale regardless of how many/which models participate.
     */
    public static Map<String, Double> normalizeWeights(Map<String, Double> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        double sum = raw.values().stream().mapToDouble(v -> v != null ? Math.max(0.0, v) : 0.0).sum();
        Map<String, Double> normalized = new LinkedHashMap<>();
        if (sum <= 0.0) {
            double equalShare = 1.0 / raw.size();
            raw.keySet().forEach(k -> normalized.put(k, equalShare));
            return normalized;
        }
        raw.forEach((k, v) -> normalized.put(k, Math.max(0.0, v != null ? v : 0.0) / sum));
        return normalized;
    }

    private List<String> buildReasons(Aggregate aggregate, int totalModels) {
        List<String> reasons = new ArrayList<>();
        if (totalModels > 1 && aggregate.voteCount == totalModels) {
            reasons.add("全部参与模型一致覆盖");
        } else if (aggregate.voteCount > 1) {
            reasons.add("多个模型共同覆盖");
        }
        if (aggregate.perModelScores.size() > 1 && stdDev(aggregate.perModelScores.values()) <= STABLE_SCORE_STDDEV_THRESHOLD) {
            reasons.add("各模型评分接近，统计信号稳定");
        }
        if (reasons.isEmpty()) {
            reasons.add("来自单一模型的统计候选");
        }
        return reasons;
    }

    private double stdDev(java.util.Collection<Integer> values) {
        double mean = values.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        double variance = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0.0);
        return Math.sqrt(variance);
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private static final class Aggregate {
        double rawScore;
        int voteCount;
        final LinkedHashSet<String> sourceModels = new LinkedHashSet<>();
        final Map<String, Integer> perModelScores = new LinkedHashMap<>();
    }
}
