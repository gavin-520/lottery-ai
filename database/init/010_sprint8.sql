-- Sprint 8: notification delivery, auto-retry metadata

ALTER TABLE data_sync_log
    ADD COLUMN retry_count   INT          DEFAULT 0 AFTER parent_log_id,
    ADD COLUMN next_retry_at DATETIME     DEFAULT NULL AFTER retry_count,
    ADD INDEX idx_sync_auto_retry (status, next_retry_at);

CREATE TABLE IF NOT EXISTS notification_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel         VARCHAR(32)  NOT NULL DEFAULT 'webhook' COMMENT 'webhook | sse',
    event_type      VARCHAR(64)  NOT NULL,
    target_url      VARCHAR(512) DEFAULT NULL,
    payload         TEXT         DEFAULT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'SUCCESS | FAILED | SKIPPED',
    http_status     INT          DEFAULT NULL,
    error_message   VARCHAR(512) DEFAULT NULL,
    correlation_id  VARCHAR(64)  DEFAULT NULL,
    breach_id       BIGINT       DEFAULT NULL,
    sync_log_id     BIGINT       DEFAULT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notify_status (status),
    INDEX idx_notify_created (created_at),
    INDEX idx_notify_correlation (correlation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
