package com.lottery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Sprint 10-E: audit record of every FC3D production-model switch (activate) or
 * disablement (deactivate). Read-only history — never influences prediction itself.
 */
@Data
@TableName("fc3d_model_switch_log")
public class Fc3dModelSwitchLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String fromVersion;
    /** Null for a pure "deactivate" entry (no confirmed replacement was promoted). */
    private String toVersion;
    private String operator;
    private String reason;
    private LocalDateTime createdTime;
}
