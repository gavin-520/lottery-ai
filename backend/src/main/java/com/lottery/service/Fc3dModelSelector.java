package com.lottery.service;

import com.lottery.dto.Fc3dModelSelectionResult;
import com.lottery.entity.Fc3dModelMetricEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 10-D §3: recommends the best-performing, currently-eligible FC3D model version based
 * on the most recent persisted walk-forward results. Advisory only: it does not itself change
 * what Predict serves — an operator (or an explicit "promote" call) applies the recommendation.
 *
 * <p>Ranking rule, in order: {@code top50HitRate} desc, then {@code top20HitRate} desc, then
 * {@code improvementVsRandom} desc, then model version ascending (stable tie-break so identical
 * metrics always resolve to the same winner). Only registered + ACTIVE model versions are
 * considered — INACTIVE (disabled) versions are never selected, however strong their metrics.</p>
 */
@Service
public class Fc3dModelSelector {

    private static final int DEFAULT_RECENT_N = 20;

    private final Fc3dModelMetricService fc3dModelMetricService;
    private final Fc3dModelRegistryService fc3dModelRegistryService;

    public Fc3dModelSelector(Fc3dModelMetricService fc3dModelMetricService,
                             Fc3dModelRegistryService fc3dModelRegistryService) {
        this.fc3dModelMetricService = fc3dModelMetricService;
        this.fc3dModelRegistryService = fc3dModelRegistryService;
    }

    public Fc3dModelSelectionResult selectBest(int recentN) {
        return selectBest(recentN, LocalDateTime.now());
    }

    /**
     * @param asOf only metric rows recorded at or before this instant are considered — guarantees
     *             future evaluation data can never influence a past selection decision.
     */
    public Fc3dModelSelectionResult selectBest(int recentN, LocalDateTime asOf) {
        int n = recentN <= 0 ? DEFAULT_RECENT_N : recentN;
        List<Fc3dModelMetricEntity> recent = fc3dModelMetricService.recent(n, asOf);

        // Most-recent-first ordering already guaranteed by the mapper query; keep the latest row per version.
        Map<String, Fc3dModelMetricEntity> latestPerVersion = new LinkedHashMap<>();
        for (Fc3dModelMetricEntity metric : recent) {
            latestPerVersion.putIfAbsent(metric.getModelVersion(), metric);
        }

        List<Fc3dModelMetricEntity> candidates = new ArrayList<>();
        for (Fc3dModelMetricEntity metric : latestPerVersion.values()) {
            if (fc3dModelRegistryService.isActive(metric.getModelVersion())) {
                candidates.add(metric);
            }
        }

        if (candidates.isEmpty()) {
            return new Fc3dModelSelectionResult(null,
                    List.of("暂无可用的已注册且启用中的模型评估记录"));
        }

        candidates.sort(Comparator
                .comparingDouble((Fc3dModelMetricEntity m) -> orZero(m.getTop50HitRate())).reversed()
                .thenComparing(Comparator.comparingDouble((Fc3dModelMetricEntity m) -> orZero(m.getTop20HitRate())).reversed())
                .thenComparing(Comparator.comparingDouble((Fc3dModelMetricEntity m) -> orZero(m.getImprovementVsRandom())).reversed())
                .thenComparing(Fc3dModelMetricEntity::getModelVersion));

        Fc3dModelMetricEntity best = candidates.get(0);
        return new Fc3dModelSelectionResult(best.getModelVersion(), buildReasons(best, candidates));
    }

    private List<String> buildReasons(Fc3dModelMetricEntity best, List<Fc3dModelMetricEntity> candidates) {
        List<String> reasons = new ArrayList<>();
        boolean topTop50 = candidates.stream().allMatch(m -> orZero(m.getTop50HitRate()) <= orZero(best.getTop50HitRate()));
        if (topTop50) {
            reasons.add("Top50覆盖率最高");
        }
        boolean topTop20 = candidates.stream().allMatch(m -> orZero(m.getTop20HitRate()) <= orZero(best.getTop20HitRate()));
        if (topTop20) {
            reasons.add("Top20覆盖率最高");
        }
        boolean topImprovement = candidates.stream()
                .allMatch(m -> orZero(m.getImprovementVsRandom()) <= orZero(best.getImprovementVsRandom()));
        if (topImprovement) {
            reasons.add("随机基准提升最大");
        }
        if (reasons.isEmpty()) {
            reasons.add("综合指标排序最优");
        }
        return reasons;
    }

    private double orZero(Double value) {
        return value != null ? value : 0.0;
    }
}
