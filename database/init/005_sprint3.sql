-- Sprint 3: data sync log

CREATE TABLE IF NOT EXISTS data_sync_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    source        VARCHAR(64)  NOT NULL,
    status        VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    fetched_count INT          NOT NULL DEFAULT 0,
    new_count     INT          NOT NULL DEFAULT 0,
    message       TEXT         DEFAULT NULL,
    started_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at   DATETIME     DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
