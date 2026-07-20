package com.lottery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lottery.entity.Fc3dModelMetricEntity;
import com.lottery.mapper.Fc3dModelMetricMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sprint 10-D: persists FC3D model walk-forward evaluation results (Fc3dModelEvaluationService /
 * Fc3dModelEvaluationResult / Fc3dExperimentResult) for historical model comparison and to drive
 * {@code Fc3dModelSelector}. Statistical record-keeping only — never generates numbers.
 */
@Service
public class Fc3dModelMetricService {

    private static final Logger log = LoggerFactory.getLogger(Fc3dModelMetricService.class);

    private final Fc3dModelMetricMapper fc3dModelMetricMapper;
    private final ObjectMapper objectMapper;

    public Fc3dModelMetricService(Fc3dModelMetricMapper fc3dModelMetricMapper, ObjectMapper objectMapper) {
        this.fc3dModelMetricMapper = fc3dModelMetricMapper;
        this.objectMapper = objectMapper;
    }

    public Fc3dModelMetricEntity record(String modelVersion, String modelName, int evaluatePeriods,
                                        double top10HitRate, double top20HitRate, double top50HitRate,
                                        double improvementVsRandom, double improvementVsFrequency,
                                        Map<String, Double> parameters) {
        Fc3dModelMetricEntity entity = new Fc3dModelMetricEntity();
        entity.setModelVersion(modelVersion);
        entity.setModelName(modelName);
        entity.setEvaluatePeriods(evaluatePeriods);
        entity.setTop10HitRate(top10HitRate);
        entity.setTop20HitRate(top20HitRate);
        entity.setTop50HitRate(top50HitRate);
        entity.setImprovementVsRandom(improvementVsRandom);
        entity.setImprovementVsFrequency(improvementVsFrequency);
        entity.setParametersJson(toJson(parameters));
        entity.setCreatedTime(LocalDateTime.now());
        fc3dModelMetricMapper.insert(entity);
        return entity;
    }

    /** Sprint 10-D: supports historical model comparison — all recorded evaluations for a version. */
    public List<Fc3dModelMetricEntity> history(String modelVersion, int limit) {
        int max = limit <= 0 ? 50 : limit;
        return fc3dModelMetricMapper.selectList(new LambdaQueryWrapper<Fc3dModelMetricEntity>()
                .eq(Fc3dModelMetricEntity::getModelVersion, modelVersion)
                .orderByDesc(Fc3dModelMetricEntity::getCreatedTime)
                .last("LIMIT " + max));
    }

    /**
     * Most recent N metric rows across all model versions, at or before {@code asOf}.
     *
     * <p>The {@code asOf} cutoff is enforced both in SQL (so real queries stay efficient) AND
     * again in-memory afterwards — a defensive second filter that guarantees a metric row
     * recorded "in the future" relative to {@code asOf} can never influence a past selection
     * decision, regardless of how the underlying query executed.</p>
     */
    public List<Fc3dModelMetricEntity> recent(int limit, LocalDateTime asOf) {
        int max = limit <= 0 ? 50 : limit;
        LambdaQueryWrapper<Fc3dModelMetricEntity> query = new LambdaQueryWrapper<Fc3dModelMetricEntity>()
                .orderByDesc(Fc3dModelMetricEntity::getCreatedTime)
                .last("LIMIT " + max);
        if (asOf != null) {
            query.le(Fc3dModelMetricEntity::getCreatedTime, asOf);
        }
        List<Fc3dModelMetricEntity> rows = fc3dModelMetricMapper.selectList(query);
        if (asOf == null) {
            return rows;
        }
        return rows.stream()
                .filter(m -> m.getCreatedTime() == null || !m.getCreatedTime().isAfter(asOf))
                .toList();
    }

    public Optional<Fc3dModelMetricEntity> latestFor(String modelVersion) {
        List<Fc3dModelMetricEntity> rows = fc3dModelMetricMapper.selectList(new LambdaQueryWrapper<Fc3dModelMetricEntity>()
                .eq(Fc3dModelMetricEntity::getModelVersion, modelVersion)
                .orderByDesc(Fc3dModelMetricEntity::getCreatedTime)
                .last("LIMIT 1"));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private String toJson(Map<String, Double> parameters) {
        if (parameters == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (Exception ex) {
            log.warn("Failed to serialize FC3D model parameters: {}", ex.getMessage());
            return null;
        }
    }
}
