-- Sprint 6: sync ops metadata, SLA breach alerts, correlation tracing

ALTER TABLE data_sync_log
    ADD COLUMN region         VARCHAR(32)  DEFAULT 'local' AFTER message,
    ADD COLUMN correlation_id VARCHAR(64)  DEFAULT NULL AFTER region,
    ADD COLUMN error_type     VARCHAR(64)  DEFAULT NULL AFTER correlation_id,
    ADD COLUMN http_status    INT          DEFAULT NULL AFTER error_type,
    ADD INDEX idx_sync_status (status),
    ADD INDEX idx_sync_region (region),
    ADD INDEX idx_sync_correlation (correlation_id);

ALTER TABLE external_api_sla_log
    ADD COLUMN correlation_id VARCHAR(64) DEFAULT NULL AFTER region,
    ADD INDEX idx_sla_correlation (correlation_id);

CREATE TABLE IF NOT EXISTS sla_breach_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    metric          VARCHAR(64)  NOT NULL COMMENT 'success_rate | p95_latency | call_failure',
    threshold_value DOUBLE       NOT NULL,
    actual_value    DOUBLE       NOT NULL,
    severity        VARCHAR(16)  NOT NULL DEFAULT 'WARN',
    region          VARCHAR(32)  NOT NULL DEFAULT 'local',
    correlation_id  VARCHAR(64)  DEFAULT NULL,
    message         VARCHAR(512) DEFAULT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_breach_region (region),
    INDEX idx_breach_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE platform_event
    ADD INDEX idx_correlation_id (correlation_id);
