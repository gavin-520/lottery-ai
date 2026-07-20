package com.lottery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lottery.config.Fc3dModelConfig;
import com.lottery.dto.Fc3dModelHealthResponse;
import com.lottery.dto.Fc3dModelInfo;
import com.lottery.dto.Fc3dModelRollbackSuggestion;
import com.lottery.dto.Fc3dModelSwitchRecord;
import com.lottery.entity.Fc3dModelMetricEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Sprint 10-E §6: verifies the FC3D model registry's DB-backed persistence, switch-log audit
 * trail, INACTIVE-model prediction exclusion, rollback advisory, and health-check status.
 *
 * <p>"Restart" is simulated by constructing a brand-new {@code Fc3dModelRegistryService}
 * instance against the SAME underlying {@link Fc3dModelRegistryMapperFake} /
 * {@link Fc3dModelSwitchLogMapperFake} "tables" — this proves persistence really lives in the
 * (fake) database rows and not in any transient field of the service object itself.</p>
 */
@ExtendWith(MockitoExtension.class)
class Fc3dModelRegistryPersistenceTest {

    private Fc3dModelConfig baseConfig;
    private Fc3dModelRegistryMapperFake registryMapper;
    private Fc3dModelSwitchLogMapperFake switchLogMapper;

    @Mock
    private Fc3dModelMetricService metricService;

    @BeforeEach
    void setUp() {
        baseConfig = new Fc3dModelConfig();
        registryMapper = new Fc3dModelRegistryMapperFake();
        switchLogMapper = new Fc3dModelSwitchLogMapperFake();
        lenient().when(metricService.latestFor(any())).thenReturn(Optional.empty());
    }

    private Fc3dModelRegistryService newRegistryService() {
        return new Fc3dModelRegistryService(baseConfig, registryMapper, switchLogMapper, metricService, new ObjectMapper());
    }

    // ------------------------------------------------------------------
    // 1. Active model persists across a restart.
    // ------------------------------------------------------------------

    @Test
    void activeModelSurvivesRestart_afterExplicitActivation() {
        Fc3dModelRegistryService serviceBeforeRestart = newRegistryService();
        serviceBeforeRestart.register("v3-exp", weights(0.05, 0.05, 0.05, 0.05, 0.80));
        serviceBeforeRestart.activate("v3-exp", "alice", "推广实验模型");

        // Simulate a service restart: a fresh instance, same underlying persisted rows.
        Fc3dModelRegistryService serviceAfterRestart = newRegistryService();

        Optional<Fc3dModelInfo> active = serviceAfterRestart.getActiveModel();
        assertTrue(active.isPresent(), "GET /model/status must still resolve a production model after restart");
        assertEquals("v3-exp", active.get().getVersion());
        assertTrue(active.get().isProduction());
    }

    @Test
    void bootstrapDefaultModel_isNotReRunOrReset_onSubsequentRestarts() {
        Fc3dModelRegistryService serviceBeforeRestart = newRegistryService();
        String bootstrapVersion = serviceBeforeRestart.getActiveModel().orElseThrow().getVersion();
        assertEquals(baseConfig.getCombinationVersion(), bootstrapVersion);

        Fc3dModelRegistryService serviceAfterRestart = newRegistryService();
        assertEquals(bootstrapVersion, serviceAfterRestart.getActiveModel().orElseThrow().getVersion());
        assertEquals(1, registryMapper.selectList(null).size(),
                "bootstrap must only ever seed the table once, never duplicate on restart");
    }

    // ------------------------------------------------------------------
    // 2. activate / deactivate write to the switch-log audit trail.
    // ------------------------------------------------------------------

    @Test
    void activate_writesSwitchLogEntry() {
        Fc3dModelRegistryService service = newRegistryService();
        service.register("v4", weights(0.30, 0.20, 0.25, 0.15, 0.10));

        service.activate("v4", "alice", "提升Top50命中率");

        List<Fc3dModelSwitchRecord> log = service.getSwitchLog();
        assertTrue(log.stream().anyMatch(r -> "v4".equals(r.getToVersion())
                        && "alice".equals(r.getOperator())
                        && "提升Top50命中率".equals(r.getReason())),
                "activate must be recorded in fc3d_model_switch_log");
    }

    @Test
    void deactivate_writesSwitchLogEntry() {
        Fc3dModelRegistryService service = newRegistryService();
        service.register("v5", weights(0.30, 0.20, 0.25, 0.15, 0.10));

        service.deactivate("v5", "bob", "效果不佳，暂停");

        List<Fc3dModelSwitchRecord> log = service.getSwitchLog();
        assertTrue(log.stream().anyMatch(r -> "v5".equals(r.getFromVersion())
                        && r.getToVersion() == null
                        && "bob".equals(r.getOperator())
                        && "效果不佳，暂停".equals(r.getReason())),
                "deactivate must be recorded in fc3d_model_switch_log");
    }

    // ------------------------------------------------------------------
    // 3. An INACTIVE model never participates in prediction, even if still "production".
    // ------------------------------------------------------------------

    @Test
    void inactiveProductionModel_isExcludedFromAutomaticPredictResolution() {
        Fc3dModelRegistryService service = newRegistryService();
        Map<String, Double> distinctWeights = weights(0.05, 0.05, 0.05, 0.05, 0.80);
        service.register("v-risky", distinctWeights);
        service.activate("v-risky");
        assertEquals("v-risky", service.resolveConfig(null).getCombinationVersion(),
                "sanity check: while ACTIVE, the production model IS used for automatic Predict");

        service.deactivate("v-risky", "ops", "评估下降，暂停使用");

        assertFalse(service.isActive("v-risky"));
        Fc3dModelConfig effective = service.resolveConfig(null);
        assertEquals(baseConfig.getCombinationVersion(), effective.getCombinationVersion(),
                "an INACTIVE production model must never be resolved for automatic Predict traffic");
        assertEquals(baseConfig.getWeightSpan(), effective.getWeightSpan(), 1e-9,
                "falls back to the base model's own weights, not the disabled model's");
    }

    @Test
    void inactiveModel_manualOverrideStillHonored_forTraceability() {
        Fc3dModelRegistryService service = newRegistryService();
        service.register("v-manual", weights(0.05, 0.05, 0.05, 0.05, 0.80));
        service.deactivate("v-manual");

        Fc3dModelConfig effective = service.resolveConfig("v-manual");

        assertEquals("v-manual", effective.getCombinationVersion(),
                "an explicit manual override still tags the response with the requested version");
    }

    // ------------------------------------------------------------------
    // 4. Rollback detection.
    // ------------------------------------------------------------------

    @Test
    void rollbackDetection_suggestsRollback_whenTop50DeclinesBeyondThreshold() {
        Fc3dModelRegistryService registryService = newRegistryService();
        registryService.register("v3", weights(0.35, 0.20, 0.25, 0.15, 0.10));
        registryService.activate("v3");
        registryService.register("v3-exp", weights(0.05, 0.05, 0.05, 0.05, 0.80));
        registryService.activate("v3-exp", "alice", "尝试新参数");

        LocalDateTime now = LocalDateTime.now();
        List<Fc3dModelMetricEntity> declining = List.of(
                metric("v3-exp", 0.30, now),
                metric("v3-exp", 0.35, now.minusDays(1)),
                metric("v3-exp", 0.40, now.minusDays(2)),
                metric("v3-exp", 0.45, now.minusDays(3)),
                metric("v3-exp", 0.50, now.minusDays(4)));
        when(metricService.history(eq("v3-exp"), anyInt())).thenReturn(declining);

        Fc3dModelRollbackService rollbackService = new Fc3dModelRollbackService(registryService, metricService);
        Fc3dModelRollbackSuggestion suggestion = rollbackService.check(5, 0.20);

        assertTrue(suggestion.isRollbackSuggested());
        assertEquals("v3-exp", suggestion.getCurrent());
        assertEquals("v3", suggestion.getFallback(), "fallback should be the version this one replaced");
        assertTrue(suggestion.getReason().stream().anyMatch(r -> r.contains("Top50连续下降")));
    }

    @Test
    void rollbackDetection_doesNotSuggestRollback_whenTop50IsStableOrImproving() {
        Fc3dModelRegistryService registryService = newRegistryService();
        registryService.register("v3-exp", weights(0.05, 0.05, 0.05, 0.05, 0.80));
        registryService.activate("v3-exp");

        LocalDateTime now = LocalDateTime.now();
        List<Fc3dModelMetricEntity> improving = List.of(
                metric("v3-exp", 0.55, now),
                metric("v3-exp", 0.50, now.minusDays(1)),
                metric("v3-exp", 0.45, now.minusDays(2)),
                metric("v3-exp", 0.40, now.minusDays(3)),
                metric("v3-exp", 0.35, now.minusDays(4)));
        when(metricService.history(eq("v3-exp"), anyInt())).thenReturn(improving);

        Fc3dModelRollbackService rollbackService = new Fc3dModelRollbackService(registryService, metricService);
        Fc3dModelRollbackSuggestion suggestion = rollbackService.check(5, 0.20);

        assertFalse(suggestion.isRollbackSuggested());
    }

    // ------------------------------------------------------------------
    // 5. Health status.
    // ------------------------------------------------------------------

    @Test
    void health_isGood_whenProductionModelIsActiveAndRecentlyEvaluatedWithHealthyTop50() {
        Fc3dModelRegistryService registryService = newRegistryService();
        registryService.register("v3", weights(0.35, 0.20, 0.25, 0.15, 0.10));
        registryService.activate("v3");

        LocalDateTime asOf = LocalDateTime.now();
        when(metricService.history(eq("v3"), anyInt())).thenReturn(List.of(metric("v3", 0.48, asOf.minusDays(1))));

        Fc3dModelHealthService healthService = new Fc3dModelHealthService(registryService, metricService);
        Fc3dModelHealthResponse health = healthService.check(asOf);

        assertEquals("GOOD", health.getStatus());
        assertEquals("v3", health.getModel());
    }

    @Test
    void health_isFailed_whenProductionModelHasBeenDeactivated() {
        Fc3dModelRegistryService registryService = newRegistryService();
        registryService.register("v-broken", weights(0.35, 0.20, 0.25, 0.15, 0.10));
        registryService.activate("v-broken");
        registryService.deactivate("v-broken", "ops", "健康检查失败");

        when(metricService.history(eq("v-broken"), anyInt())).thenReturn(List.of());

        Fc3dModelHealthService healthService = new Fc3dModelHealthService(registryService, metricService);
        Fc3dModelHealthResponse health = healthService.check(LocalDateTime.now());

        assertEquals("FAILED", health.getStatus());
        assertTrue(health.getChecks().stream().anyMatch(c -> "production".equals(c.getName()) && "FAILED".equals(c.getStatus())));
    }

    @Test
    void health_isWarning_whenEvaluationIsStaleAndTop50IsMarginal() {
        Fc3dModelRegistryService registryService = newRegistryService();
        registryService.register("v3", weights(0.35, 0.20, 0.25, 0.15, 0.10));
        registryService.activate("v3");

        LocalDateTime asOf = LocalDateTime.now();
        when(metricService.history(eq("v3"), anyInt())).thenReturn(List.of(metric("v3", 0.20, asOf.minusDays(10))));

        Fc3dModelHealthService healthService = new Fc3dModelHealthService(registryService, metricService);
        Fc3dModelHealthResponse health = healthService.check(asOf);

        assertEquals("WARNING", health.getStatus());
    }

    private Map<String, Double> weights(double frequency, double missing, double sum, double oddEven, double span) {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("frequency", frequency);
        map.put("missing", missing);
        map.put("sum", sum);
        map.put("oddEven", oddEven);
        map.put("span", span);
        return map;
    }

    private Fc3dModelMetricEntity metric(String version, double top50, LocalDateTime createdTime) {
        Fc3dModelMetricEntity entity = new Fc3dModelMetricEntity();
        entity.setModelVersion(version);
        entity.setModelName(version);
        entity.setEvaluatePeriods(30);
        entity.setTop10HitRate(top50 * 0.4);
        entity.setTop20HitRate(top50 * 0.7);
        entity.setTop50HitRate(top50);
        entity.setImprovementVsRandom(0.1);
        entity.setImprovementVsFrequency(0.05);
        entity.setCreatedTime(createdTime);
        return entity;
    }
}
