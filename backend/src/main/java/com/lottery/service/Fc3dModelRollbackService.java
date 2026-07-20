package com.lottery.service;

import com.lottery.dto.Fc3dModelInfo;
import com.lottery.dto.Fc3dModelRollbackSuggestion;
import com.lottery.dto.Fc3dModelSwitchRecord;
import com.lottery.entity.Fc3dModelMetricEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 10-E §4: automatic rollback ADVISORY only. Detects a sustained Top50 hit-rate decline
 * in the production model's most recent evaluations and, if found, suggests a safe fallback
 * version. Never executes a rollback itself — an operator must still call
 * {@code POST /model/activate} to apply it.
 */
@Service
public class Fc3dModelRollbackService {

    private static final int DEFAULT_RECENT_N = 5;
    private static final double DEFAULT_DECLINE_THRESHOLD = 0.20;

    private final Fc3dModelRegistryService fc3dModelRegistryService;
    private final Fc3dModelMetricService fc3dModelMetricService;

    public Fc3dModelRollbackService(Fc3dModelRegistryService fc3dModelRegistryService,
                                    Fc3dModelMetricService fc3dModelMetricService) {
        this.fc3dModelRegistryService = fc3dModelRegistryService;
        this.fc3dModelMetricService = fc3dModelMetricService;
    }

    public Fc3dModelRollbackSuggestion check() {
        return check(DEFAULT_RECENT_N, DEFAULT_DECLINE_THRESHOLD);
    }

    /**
     * @param recentN           how many most-recent evaluations of the production model to inspect.
     * @param declineThreshold  fractional Top50 decline (oldest vs. latest in the window) that
     *                          triggers a suggestion, e.g. {@code 0.20} for a 20% drop.
     */
    public Fc3dModelRollbackSuggestion check(int recentN, double declineThreshold) {
        Optional<Fc3dModelInfo> active = fc3dModelRegistryService.getActiveModel();
        if (active.isEmpty()) {
            return new Fc3dModelRollbackSuggestion(false, null, null, List.of("暂无生产模型，无需回滚"));
        }
        String current = active.get().getVersion();
        int n = recentN <= 0 ? DEFAULT_RECENT_N : recentN;
        double threshold = declineThreshold <= 0 ? DEFAULT_DECLINE_THRESHOLD : declineThreshold;

        List<Fc3dModelMetricEntity> historyDesc = fc3dModelMetricService.history(current, n);
        if (historyDesc.size() < 2) {
            return new Fc3dModelRollbackSuggestion(false, current, null, List.of("评估记录不足，无法判断趋势"));
        }

        List<Fc3dModelMetricEntity> chronological = new ArrayList<>(historyDesc);
        Collections.reverse(chronological);

        double oldest = orZero(chronological.get(0).getTop50HitRate());
        double latest = orZero(chronological.get(chronological.size() - 1).getTop50HitRate());

        if (oldest <= 0) {
            return new Fc3dModelRollbackSuggestion(false, current, null, List.of("历史基准为 0，无法判断下降比例"));
        }

        double decline = (oldest - latest) / oldest;
        if (decline < threshold) {
            return new Fc3dModelRollbackSuggestion(false, current, null, List.of());
        }

        List<String> reasons = new ArrayList<>();
        reasons.add("Top50连续下降");
        reasons.add(String.format("近%d次评估 Top50 由 %.2f 降至 %.2f（-%.1f%%）", chronological.size(), oldest, latest, decline * 100));

        String fallback = determineFallback(current);
        if (fallback == null) {
            reasons.add("未找到可用的历史回滚版本");
        }
        return new Fc3dModelRollbackSuggestion(true, current, fallback, reasons);
    }

    /** Prefers the version this one most recently replaced; falls back to the best other ACTIVE, evaluated model. */
    private String determineFallback(String current) {
        String previous = fc3dModelRegistryService.getSwitchLog().stream()
                .filter(r -> current.equals(r.getToVersion()))
                .map(Fc3dModelSwitchRecord::getFromVersion)
                .filter(v -> v != null && !v.equals(current))
                .findFirst()
                .orElse(null);
        if (previous != null) {
            return previous;
        }
        return fc3dModelRegistryService.listModels().stream()
                .filter(m -> !m.getVersion().equals(current))
                .filter(m -> Fc3dModelRegistryService.STATUS_ACTIVE.equals(m.getStatus()))
                .filter(m -> m.getMetrics() != null)
                .max(Comparator.comparingDouble(m -> m.getMetrics().getTop50HitRate()))
                .map(Fc3dModelInfo::getVersion)
                .orElse(null);
    }

    private double orZero(Double value) {
        return value != null ? value : 0.0;
    }
}
