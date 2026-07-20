-- Sprint 10-E: FC3D model registry + switch-log persistence (production model lifecycle).
-- Additive only — does not touch any existing lottery data table, including fc3d_model_metric.

CREATE TABLE IF NOT EXISTS fc3d_model_registry (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_version     VARCHAR(64)  NOT NULL UNIQUE COMMENT '模型版本，如 v3 / v3-experiment-002',
    model_name        VARCHAR(64)  DEFAULT NULL COMMENT '模型名称',
    status            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE | INACTIVE',
    parameters_json   TEXT         DEFAULT NULL COMMENT '注册时的权重参数（JSON）',
    created_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_time     DATETIME    DEFAULT NULL COMMENT '最近一次被设为生产模型的时间',
    deactivated_time   DATETIME    DEFAULT NULL COMMENT '最近一次被停用的时间',
    is_production     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否为当前生产（Predict 生效）模型，全表至多一条为 1',
    INDEX idx_fc3d_model_registry_version (model_version),
    INDEX idx_fc3d_model_registry_production (is_production)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fc3d_model_switch_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_version   VARCHAR(64)  DEFAULT NULL COMMENT '切换前版本，初始注册时为空',
    to_version     VARCHAR(64)  DEFAULT NULL COMMENT '切换后版本；停用操作时为空',
    operator       VARCHAR(64)  DEFAULT NULL COMMENT '操作人（用户名），系统自动触发时为 system',
    reason         VARCHAR(512) DEFAULT NULL COMMENT '切换/停用原因',
    created_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_fc3d_switch_log_created (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
