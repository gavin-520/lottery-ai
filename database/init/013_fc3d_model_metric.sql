-- Sprint 10-D: FC3D model evaluation history (persisted walk-forward metrics).
-- Additive only — does not touch any existing lottery data table.

CREATE TABLE IF NOT EXISTS fc3d_model_metric (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_version           VARCHAR(64)  NOT NULL COMMENT '模型版本，如 v3 / v3-experiment-002',
    model_name              VARCHAR(64)  NOT NULL COMMENT '模型名称，如 fc3d-combination-model',
    evaluate_periods        INT          NOT NULL DEFAULT 0 COMMENT 'walk-forward 评估期数',
    top10_hit_rate          DOUBLE       NOT NULL DEFAULT 0 COMMENT 'Top10 命中率',
    top20_hit_rate          DOUBLE       NOT NULL DEFAULT 0 COMMENT 'Top20 命中率',
    top50_hit_rate          DOUBLE       NOT NULL DEFAULT 0 COMMENT 'Top50 命中率',
    improvement_vs_random   DOUBLE       NOT NULL DEFAULT 0 COMMENT '相对随机基准的提升比例',
    improvement_vs_frequency DOUBLE      NOT NULL DEFAULT 0 COMMENT '相对纯频率模型的提升比例',
    parameters_json         TEXT         DEFAULT NULL COMMENT '本次评估使用的权重参数（JSON）',
    created_time            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_fc3d_model_metric_version (model_version),
    INDEX idx_fc3d_model_metric_created (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
