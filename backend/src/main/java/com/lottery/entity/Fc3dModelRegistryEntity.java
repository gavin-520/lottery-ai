package com.lottery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Sprint 10-E: persisted FC3D model registry row — replaces the in-memory map used in
 * Sprint 10-D so the currently-ACTIVE production model survives a service restart.
 * Never generates numbers — this is model-lifecycle bookkeeping only.
 */
@Data
@TableName("fc3d_model_registry")
public class Fc3dModelRegistryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String modelVersion;
    private String modelName;
    /** ACTIVE (enabled) | INACTIVE (disabled — excluded from auto-selection AND from Predict). */
    private String status;
    /** JSON-serialized weight parameters used by this registered version. */
    private String parametersJson;
    private LocalDateTime createdTime;
    private LocalDateTime activatedTime;
    private LocalDateTime deactivatedTime;
    /** At most one row in the table has this set to true — the current production model. */
    private Boolean isProduction;
}
