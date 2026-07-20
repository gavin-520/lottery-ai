package com.lottery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Sprint 10-D: persisted walk-forward evaluation record for a single FC3D model
 * version — supports historical model comparison and drives {@code Fc3dModelSelector}.
 * Statistical evaluation only — never a guarantee of any outcome.
 */
@Data
@TableName("fc3d_model_metric")
public class Fc3dModelMetricEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String modelVersion;
    private String modelName;
    private Integer evaluatePeriods;
    private Double top10HitRate;
    private Double top20HitRate;
    private Double top50HitRate;
    private Double improvementVsRandom;
    private Double improvementVsFrequency;
    /** JSON-serialized weight parameters used for this evaluation run. */
    private String parametersJson;
    private LocalDateTime createdTime;
}
