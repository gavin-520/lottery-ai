package com.lottery.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sprint 11-B §4: holds the LAST manually-applied Ensemble fusion weight override, if any.
 *
 * <p>Never written to automatically — {@link Fc3dEnsembleWeightOptimizer} only ever returns a
 * recommendation; a human must explicitly call {@link #apply} (via {@code POST
 * /ensemble/apply-weights}, itself gated behind a confirmation dialog in the UI) before it takes
 * effect. Deliberately in-memory only (no DB migration for this Sprint's narrow scope) — a
 * restart simply reverts to the Sprint 11-A registry role-based default weights, which is a safe
 * fallback, never a broken state.</p>
 */
@Service
public class Fc3dEnsembleWeightOverrideService {

    private volatile List<String> appliedVersions = List.of();
    private volatile Map<String, Double> appliedWeights = Map.of();
    private volatile LocalDateTime appliedAt;

    /** Applies a new override for EXACTLY this set of model versions (order-independent). */
    public synchronized void apply(List<String> versions, Map<String, Double> weights) {
        this.appliedVersions = sortedCopy(versions);
        this.appliedWeights = new LinkedHashMap<>(weights);
        this.appliedAt = LocalDateTime.now();
    }

    public synchronized void clear() {
        this.appliedVersions = List.of();
        this.appliedWeights = Map.of();
        this.appliedAt = null;
    }

    /** Only returns an override when {@code versions} is EXACTLY the set it was applied for — otherwise falls back to role-based defaults. */
    public Optional<Map<String, Double>> resolve(List<String> versions) {
        if (appliedWeights.isEmpty()) {
            return Optional.empty();
        }
        if (!sortedCopy(versions).equals(appliedVersions)) {
            return Optional.empty();
        }
        return Optional.of(new LinkedHashMap<>(appliedWeights));
    }

    public Optional<Map<String, Double>> current() {
        return appliedWeights.isEmpty() ? Optional.empty() : Optional.of(new LinkedHashMap<>(appliedWeights));
    }

    public List<String> currentVersions() {
        return appliedVersions;
    }

    public LocalDateTime appliedAt() {
        return appliedAt;
    }

    private List<String> sortedCopy(List<String> versions) {
        List<String> copy = new ArrayList<>(versions != null ? versions : List.of());
        copy.sort(String::compareTo);
        return copy;
    }
}
