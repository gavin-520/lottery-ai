package com.lottery.service;

import com.lottery.dto.Fc3dModelHealthCheck;
import com.lottery.dto.Fc3dModelHealthResponse;
import com.lottery.dto.Fc3dModelInfo;
import com.lottery.entity.Fc3dModelMetricEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 10-E §3: read-only health snapshot of the current production FC3D model. Advisory
 * only — never changes the active model, never influences candidate scoring.
 *
 * <p>Four checks, each independently GOOD / WARNING / FAILED; the overall {@code status} is
 * the worst of the four:</p>
 * <ol>
 *     <li>{@code lastEvaluation} — how long since the production model was last evaluated.</li>
 *     <li>{@code top50} — the production model's most recent Top50 hit rate level.</li>
 *     <li>{@code modelFreshness} — how long the production model has run without being replaced.</li>
 *     <li>{@code production} — whether a production model exists and is still ACTIVE.</li>
 * </ol>
 */
@Service
public class Fc3dModelHealthService {

    public static final String GOOD = "GOOD";
    public static final String WARNING = "WARNING";
    public static final String FAILED = "FAILED";

    private static final int TREND_WINDOW = 5;
    private static final long LAST_EVAL_WARNING_DAYS = 7;
    private static final long LAST_EVAL_FAILED_DAYS = 30;
    private static final double TOP50_GOOD_THRESHOLD = 0.30;
    private static final double TOP50_WARNING_THRESHOLD = 0.15;
    private static final long FRESHNESS_WARNING_DAYS = 90;

    private final Fc3dModelRegistryService fc3dModelRegistryService;
    private final Fc3dModelMetricService fc3dModelMetricService;

    public Fc3dModelHealthService(Fc3dModelRegistryService fc3dModelRegistryService,
                                  Fc3dModelMetricService fc3dModelMetricService) {
        this.fc3dModelRegistryService = fc3dModelRegistryService;
        this.fc3dModelMetricService = fc3dModelMetricService;
    }

    public Fc3dModelHealthResponse check() {
        return check(LocalDateTime.now());
    }

    /** @param asOf reference "now" instant — kept explicit so tests are fully deterministic. */
    public Fc3dModelHealthResponse check(LocalDateTime asOf) {
        Optional<Fc3dModelInfo> active = fc3dModelRegistryService.getActiveModel();
        String modelVersion = active.map(Fc3dModelInfo::getVersion).orElse(null);
        List<Fc3dModelMetricEntity> historyDesc = modelVersion != null
                ? fc3dModelMetricService.history(modelVersion, TREND_WINDOW)
                : List.of();

        List<Fc3dModelHealthCheck> checks = new ArrayList<>();
        checks.add(checkProductionExists(active));
        checks.add(checkLastEvaluation(historyDesc, asOf));
        checks.add(checkTop50(historyDesc));
        checks.add(checkFreshness(active, asOf));

        return new Fc3dModelHealthResponse(worstStatus(checks), modelVersion, checks);
    }

    private Fc3dModelHealthCheck checkProductionExists(Optional<Fc3dModelInfo> active) {
        if (active.isEmpty()) {
            return new Fc3dModelHealthCheck("production", null, FAILED, "不存在生产模型");
        }
        Fc3dModelInfo info = active.get();
        if (!Fc3dModelRegistryService.STATUS_ACTIVE.equals(info.getStatus())) {
            return new Fc3dModelHealthCheck("production", null, FAILED, "生产模型 " + info.getVersion() + " 已被停用");
        }
        return new Fc3dModelHealthCheck("production", null, GOOD, "生产模型 " + info.getVersion() + " 正常运行");
    }

    private Fc3dModelHealthCheck checkLastEvaluation(List<Fc3dModelMetricEntity> historyDesc, LocalDateTime asOf) {
        if (historyDesc.isEmpty()) {
            return new Fc3dModelHealthCheck("lastEvaluation", null, FAILED, "尚无评估记录");
        }
        LocalDateTime last = historyDesc.get(0).getCreatedTime();
        if (last == null) {
            return new Fc3dModelHealthCheck("lastEvaluation", null, FAILED, "评估记录时间缺失");
        }
        long days = Duration.between(last, asOf).toDays();
        String status = days > LAST_EVAL_FAILED_DAYS ? FAILED : days > LAST_EVAL_WARNING_DAYS ? WARNING : GOOD;
        return new Fc3dModelHealthCheck("lastEvaluation", (double) days, status, "距最近一次评估已 " + days + " 天");
    }

    private Fc3dModelHealthCheck checkTop50(List<Fc3dModelMetricEntity> historyDesc) {
        if (historyDesc.isEmpty()) {
            return new Fc3dModelHealthCheck("top50", null, FAILED, "尚无 Top50 命中率数据");
        }
        double latest = orZero(historyDesc.get(0).getTop50HitRate());
        String status = latest >= TOP50_GOOD_THRESHOLD ? GOOD
                : latest >= TOP50_WARNING_THRESHOLD ? WARNING : FAILED;
        return new Fc3dModelHealthCheck("top50", latest, status, "最近一次 Top50 命中率 " + latest);
    }

    private Fc3dModelHealthCheck checkFreshness(Optional<Fc3dModelInfo> active, LocalDateTime asOf) {
        if (active.isEmpty() || active.get().getCreatedTime() == null) {
            return new Fc3dModelHealthCheck("modelFreshness", null, WARNING, "无法判断模型更新时间");
        }
        long days = Duration.between(active.get().getCreatedTime(), asOf).toDays();
        // Staleness alone is never fatal — it just means nobody has iterated on the model recently.
        String status = days > FRESHNESS_WARNING_DAYS ? WARNING : GOOD;
        return new Fc3dModelHealthCheck("modelFreshness", (double) days, status, "当前生产模型已运行 " + days + " 天未更换版本");
    }

    private String worstStatus(List<Fc3dModelHealthCheck> checks) {
        return checks.stream()
                .map(Fc3dModelHealthCheck::getStatus)
                .max(Comparator.comparingInt(this::severity))
                .orElse(GOOD);
    }

    private int severity(String status) {
        if (FAILED.equals(status)) return 2;
        if (WARNING.equals(status)) return 1;
        return 0;
    }

    private double orZero(Double value) {
        return value != null ? value : 0.0;
    }
}
