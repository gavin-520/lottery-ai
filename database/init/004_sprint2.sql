-- Sprint 2: import job tracking

CREATE TABLE IF NOT EXISTS import_job (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    filename      VARCHAR(255) NOT NULL,
    total_rows    INT          NOT NULL DEFAULT 0,
    success_rows  INT          NOT NULL DEFAULT 0,
    failed_rows   INT          NOT NULL DEFAULT 0,
    status        VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    error_message TEXT         DEFAULT NULL,
    created_by    BIGINT       DEFAULT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
