-- Sprint 5: Avro metadata, external API SLA, multi-region

ALTER TABLE platform_event
    ADD COLUMN schema_version  VARCHAR(32)  DEFAULT '1.0' AFTER payload,
    ADD COLUMN region          VARCHAR(32)  DEFAULT 'local' AFTER schema_version,
    ADD COLUMN correlation_id  VARCHAR(64)  DEFAULT NULL AFTER region,
    ADD INDEX idx_region (region);

CREATE TABLE IF NOT EXISTS external_api_sla_log (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider     VARCHAR(64)  NOT NULL DEFAULT 'external-api',
    endpoint     VARCHAR(512) NOT NULL,
    latency_ms   INT          NOT NULL DEFAULT 0,
    http_status  INT          DEFAULT NULL,
    success      TINYINT      NOT NULL DEFAULT 0 COMMENT '1=success 0=failure',
    error_type   VARCHAR(64)  DEFAULT NULL,
    region       VARCHAR(32)  NOT NULL DEFAULT 'local',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_provider (provider),
    INDEX idx_success (success),
    INDEX idx_created_at (created_at),
    INDEX idx_region (region)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
