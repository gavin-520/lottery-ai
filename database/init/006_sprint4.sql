-- Sprint 4: platform event audit log

CREATE TABLE IF NOT EXISTS platform_event (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type  VARCHAR(64)  NOT NULL,
    topic       VARCHAR(128) NOT NULL,
    payload     JSON         DEFAULT NULL,
    published   TINYINT      NOT NULL DEFAULT 0 COMMENT '1=kafka published',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_event_type (event_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
