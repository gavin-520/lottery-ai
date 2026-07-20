package com.lottery.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lottery.config.Fc3dModelConfig;
import com.lottery.dto.Fc3dModelInfo;
import com.lottery.dto.Fc3dModelMetricsSummary;
import com.lottery.dto.Fc3dModelSwitchRecord;
import com.lottery.entity.Fc3dModelMetricEntity;
import com.lottery.entity.Fc3dModelRegistryEntity;
import com.lottery.entity.Fc3dModelSwitchLogEntity;
import com.lottery.mapper.Fc3dModelRegistryMapper;
import com.lottery.mapper.Fc3dModelSwitchLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sprint 10-E: database-backed registry of known FC3D model versions (weights + eligibility
 * status) and the single "production" version currently served by Predict.
 *
 * <p>Replaces the Sprint 10-D in-memory map with a {@link Fc3dModelRegistryMapper}-backed
 * repository so the currently ACTIVE production model — and the full switch-log audit trail —
 * survive a service restart. Evaluation HISTORY (Fc3dModelMetricEntity) continues to be
 * persisted separately by {@link Fc3dModelMetricService}.</p>
 */
@Service
public class Fc3dModelRegistryService {

    private static final Logger log = LoggerFactory.getLogger(Fc3dModelRegistryService.class);

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";
    private static final String DEFAULT_OPERATOR = "system";

    private final Fc3dModelConfig baseConfig;
    private final Fc3dModelRegistryMapper registryMapper;
    private final Fc3dModelSwitchLogMapper switchLogMapper;
    private final Fc3dModelMetricService fc3dModelMetricService;
    private final ObjectMapper objectMapper;

    public Fc3dModelRegistryService(Fc3dModelConfig baseConfig,
                                    Fc3dModelRegistryMapper registryMapper,
                                    Fc3dModelSwitchLogMapper switchLogMapper,
                                    Fc3dModelMetricService fc3dModelMetricService,
                                    ObjectMapper objectMapper) {
        this.baseConfig = baseConfig;
        this.registryMapper = registryMapper;
        this.switchLogMapper = switchLogMapper;
        this.fc3dModelMetricService = fc3dModelMetricService;
        this.objectMapper = objectMapper;
        bootstrapIfEmpty();
    }

    /** Only seeds the base model on a genuinely empty table — never resets an existing, persisted state. */
    private void bootstrapIfEmpty() {
        Long count = registryMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Fc3dModelRegistryEntity entity = new Fc3dModelRegistryEntity();
        entity.setModelVersion(baseConfig.getCombinationVersion());
        entity.setModelName(baseConfig.getFullModelName());
        entity.setStatus(STATUS_ACTIVE);
        entity.setParametersJson(toJson(baseConfig.toWeightMap()));
        entity.setCreatedTime(now);
        entity.setActivatedTime(now);
        entity.setIsProduction(true);
        registryMapper.insert(entity);
        insertSwitchLog(null, entity.getModelVersion(), DEFAULT_OPERATOR, "系统初始化默认生产模型");
    }

    /** Registers (or re-registers) a model version with the given weight parameters. Defaults to ACTIVE. */
    public synchronized Fc3dModelInfo register(String version, Map<String, Double> parameters) {
        Fc3dModelRegistryEntity entity = findEntity(version);
        boolean isNew = entity == null;
        if (isNew) {
            entity = new Fc3dModelRegistryEntity();
            entity.setModelVersion(version);
            entity.setCreatedTime(LocalDateTime.now());
            entity.setIsProduction(false);
        }
        entity.setModelName(baseConfig.getName() + "-" + version);
        entity.setStatus(STATUS_ACTIVE);
        entity.setParametersJson(toJson(parameters));
        if (isNew) {
            registryMapper.insert(entity);
        } else {
            registryMapper.updateById(entity);
        }
        return toInfo(entity);
    }

    public Fc3dModelInfo activate(String version) {
        return activate(version, DEFAULT_OPERATOR, null);
    }

    /**
     * Promotes a registered version to be THE model Predict serves automatically, and (re-)enables
     * it if it had been disabled. Every version switch is recorded in the switch-log audit trail.
     */
    public synchronized Fc3dModelInfo activate(String version, String operator, String reason) {
        Fc3dModelRegistryEntity entity = requireEntity(version);
        String previousProduction = currentProductionVersion();
        if (previousProduction != null && !previousProduction.equals(version)) {
            Fc3dModelRegistryEntity previousEntity = findEntity(previousProduction);
            if (previousEntity != null) {
                previousEntity.setIsProduction(false);
                registryMapper.updateById(previousEntity);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(STATUS_ACTIVE);
        entity.setActivatedTime(now);
        entity.setIsProduction(true);
        registryMapper.updateById(entity);
        if (!version.equals(previousProduction)) {
            insertSwitchLog(previousProduction, version, operatorOrDefault(operator), reason);
        }
        return toInfo(entity);
    }

    public void deactivate(String version) {
        deactivate(version, DEFAULT_OPERATOR, null);
    }

    /**
     * Disables a version — excluded from auto-selection AND from being resolved as the automatic
     * Predict model (even if it is still marked as production; see {@link #resolveConfig}).
     * Always recorded in the switch-log audit trail.
     */
    public synchronized Fc3dModelInfo deactivate(String version, String operator, String reason) {
        Fc3dModelRegistryEntity entity = requireEntity(version);
        entity.setStatus(STATUS_INACTIVE);
        entity.setDeactivatedTime(LocalDateTime.now());
        registryMapper.updateById(entity);
        insertSwitchLog(version, null, operatorOrDefault(operator), reason);
        return toInfo(entity);
    }

    public boolean isActive(String version) {
        Fc3dModelRegistryEntity entity = findEntity(version);
        return entity != null && STATUS_ACTIVE.equals(entity.getStatus());
    }

    public Optional<Fc3dModelInfo> getActiveModel() {
        Fc3dModelRegistryEntity entity = findProductionEntity();
        return entity == null ? Optional.empty() : Optional.of(toInfo(entity));
    }

    public Optional<Fc3dModelInfo> get(String version) {
        Fc3dModelRegistryEntity entity = findEntity(version);
        return entity == null ? Optional.empty() : Optional.of(toInfo(entity));
    }

    public List<Fc3dModelInfo> listModels() {
        List<Fc3dModelRegistryEntity> rows = new ArrayList<>(allEntities());
        rows.sort(Comparator.comparing(Fc3dModelRegistryEntity::getCreatedTime,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return rows.stream().map(this::toInfo).toList();
    }

    /** Sprint 10-D/E §6: most-recent-first audit log of production model switches / deactivations. */
    public List<Fc3dModelSwitchRecord> getSwitchLog() {
        List<Fc3dModelSwitchLogEntity> rows = new ArrayList<>(allSwitchLogEntities());
        rows.sort(Comparator.comparing(Fc3dModelSwitchLogEntity::getCreatedTime,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return rows.stream()
                .map(r -> new Fc3dModelSwitchRecord(r.getFromVersion(), r.getToVersion(), r.getOperator(), r.getReason(), r.getCreatedTime()))
                .toList();
    }

    /**
     * Sprint 10-D §4: resolves the effective {@link Fc3dModelConfig} for the Predict chain.
     * If {@code requestedVersion} is provided, it is used verbatim (manual override, honored
     * even if the version is INACTIVE or unknown — falls back to base weights but keeps the
     * requested version tag for traceability). Otherwise resolves the current production model
     * — but ONLY if it is still ACTIVE; an INACTIVE production model is never used to serve
     * automatic predictions (Sprint 10-E §1: "inactive 模型不会参与预测").
     */
    public Fc3dModelConfig resolveConfig(String requestedVersion) {
        String version;
        Map<String, Double> parameters = null;

        if (requestedVersion != null && !requestedVersion.isBlank()) {
            version = requestedVersion;
            Fc3dModelRegistryEntity entity = findEntity(requestedVersion);
            if (entity != null) {
                parameters = fromJson(entity.getParametersJson());
            }
        } else {
            Fc3dModelRegistryEntity production = findProductionEntity();
            if (production != null && STATUS_ACTIVE.equals(production.getStatus())) {
                version = production.getModelVersion();
                parameters = fromJson(production.getParametersJson());
            } else {
                version = baseConfig.getCombinationVersion();
            }
        }

        Fc3dModelConfig effective = copyConfig(baseConfig);
        applyOverrides(effective, parameters);
        effective.setCombinationVersion(version);
        return effective;
    }

    /**
     * All registry tables are tiny (a handful of model versions) so every lookup fetches the
     * full table and filters in Java — this keeps the service's behavior fully independent of
     * how any particular {@code Wrapper} query executes (easy to unit-test with a plain
     * in-memory fake mapper, same defensive style as {@code Fc3dModelMetricService.recent()}).
     */
    private List<Fc3dModelRegistryEntity> allEntities() {
        List<Fc3dModelRegistryEntity> rows = registryMapper.selectList(null);
        return rows != null ? rows : List.of();
    }

    private List<Fc3dModelSwitchLogEntity> allSwitchLogEntities() {
        List<Fc3dModelSwitchLogEntity> rows = switchLogMapper.selectList(null);
        return rows != null ? rows : List.of();
    }

    private Fc3dModelRegistryEntity findEntity(String version) {
        if (version == null) {
            return null;
        }
        return allEntities().stream()
                .filter(e -> version.equals(e.getModelVersion()))
                .findFirst()
                .orElse(null);
    }

    private Fc3dModelRegistryEntity findProductionEntity() {
        return allEntities().stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsProduction()))
                .findFirst()
                .orElse(null);
    }

    private String currentProductionVersion() {
        Fc3dModelRegistryEntity entity = findProductionEntity();
        return entity == null ? null : entity.getModelVersion();
    }

    private Fc3dModelRegistryEntity requireEntity(String version) {
        Fc3dModelRegistryEntity entity = findEntity(version);
        if (entity == null) {
            throw new IllegalArgumentException("unknown FC3D model version: " + version);
        }
        return entity;
    }

    private void insertSwitchLog(String from, String to, String operator, String reason) {
        Fc3dModelSwitchLogEntity entity = new Fc3dModelSwitchLogEntity();
        entity.setFromVersion(from);
        entity.setToVersion(to);
        entity.setOperator(operator);
        entity.setReason(reason);
        entity.setCreatedTime(LocalDateTime.now());
        switchLogMapper.insert(entity);
    }

    private String operatorOrDefault(String operator) {
        return operator != null && !operator.isBlank() ? operator : DEFAULT_OPERATOR;
    }

    private Fc3dModelInfo toInfo(Fc3dModelRegistryEntity entity) {
        Fc3dModelMetricsSummary metrics = fc3dModelMetricService.latestFor(entity.getModelVersion())
                .map(this::toSummary)
                .orElse(null);
        boolean production = Boolean.TRUE.equals(entity.getIsProduction());
        return new Fc3dModelInfo(entity.getModelVersion(), entity.getStatus(), entity.getCreatedTime(),
                new LinkedHashMap<>(fromJsonOrEmpty(entity.getParametersJson())), metrics, production);
    }

    private Fc3dModelMetricsSummary toSummary(Fc3dModelMetricEntity entity) {
        return new Fc3dModelMetricsSummary(
                entity.getEvaluatePeriods() != null ? entity.getEvaluatePeriods() : 0,
                entity.getTop10HitRate() != null ? entity.getTop10HitRate() : 0.0,
                entity.getTop20HitRate() != null ? entity.getTop20HitRate() : 0.0,
                entity.getTop50HitRate() != null ? entity.getTop50HitRate() : 0.0,
                entity.getImprovementVsRandom() != null ? entity.getImprovementVsRandom() : 0.0,
                entity.getImprovementVsFrequency() != null ? entity.getImprovementVsFrequency() : 0.0,
                entity.getCreatedTime());
    }

    private void applyOverrides(Fc3dModelConfig config, Map<String, Double> parameters) {
        if (parameters == null) {
            return;
        }
        if (parameters.get("frequency") != null) config.setWeightFrequency(parameters.get("frequency"));
        if (parameters.get("missing") != null) config.setWeightMissing(parameters.get("missing"));
        if (parameters.get("sum") != null) config.setWeightSum(parameters.get("sum"));
        if (parameters.get("oddEven") != null) config.setWeightOddEven(parameters.get("oddEven"));
        if (parameters.get("span") != null) config.setWeightSpan(parameters.get("span"));
    }

    private Fc3dModelConfig copyConfig(Fc3dModelConfig base) {
        Fc3dModelConfig copy = new Fc3dModelConfig();
        copy.setName(base.getName());
        copy.setVersion(base.getVersion());
        copy.setWeightFrequency(base.getWeightFrequency());
        copy.setWeightMissing(base.getWeightMissing());
        copy.setWeightSum(base.getWeightSum());
        copy.setWeightOddEven(base.getWeightOddEven());
        copy.setWeightAntiRepeatBonus(base.getWeightAntiRepeatBonus());
        copy.setWeightAntiRepeatPenalty(base.getWeightAntiRepeatPenalty());
        copy.setPoolPerPosition(base.getPoolPerPosition());
        copy.setRecentAvoidPeriods(base.getRecentAvoidPeriods());
        copy.setWeightSpan(base.getWeightSpan());
        copy.setCandidateCount(base.getCandidateCount());
        copy.setMaxPoolSize(base.getMaxPoolSize());
        copy.setCombinationVersion(base.getCombinationVersion());
        copy.setExperimentEnabled(base.isExperimentEnabled());
        copy.setExperimentMaxCombinations(base.getExperimentMaxCombinations());
        return copy;
    }

    private String toJson(Map<String, Double> parameters) {
        if (parameters == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (Exception ex) {
            log.warn("Failed to serialize FC3D model registry parameters: {}", ex.getMessage());
            return null;
        }
    }

    private Map<String, Double> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Double>>() {});
        } catch (Exception ex) {
            log.warn("Failed to deserialize FC3D model registry parameters: {}", ex.getMessage());
            return null;
        }
    }

    private Map<String, Double> fromJsonOrEmpty(String json) {
        Map<String, Double> parsed = fromJson(json);
        return parsed != null ? parsed : new LinkedHashMap<>();
    }
}
