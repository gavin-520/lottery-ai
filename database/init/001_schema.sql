-- Lottery AI Platform — Core Schema (Sprint 0)

CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    nickname    VARCHAR(64)  DEFAULT NULL,
    role        VARCHAR(32)  NOT NULL DEFAULT 'USER',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=active 0=disabled',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS lottery_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    period      VARCHAR(32)  NOT NULL UNIQUE COMMENT '期号',
    draw_date   DATE         NOT NULL,
    red_balls   VARCHAR(32)  NOT NULL COMMENT '红球,逗号分隔',
    blue_ball   VARCHAR(8)   NOT NULL COMMENT '蓝球',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS predict_record (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    period      VARCHAR(32)  NOT NULL,
    model_name  VARCHAR(64)  NOT NULL,
    red_balls   VARCHAR(32)  NOT NULL,
    blue_ball   VARCHAR(8)   NOT NULL,
    confidence  DECIMAL(5,4) DEFAULT NULL,
    created_by  BIGINT       DEFAULT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS backtest_report (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    start_period VARCHAR(32) NOT NULL,
    end_period   VARCHAR(32) NOT NULL,
    hit_rate    DECIMAL(5,4) DEFAULT NULL,
    summary     JSON         DEFAULT NULL,
    created_by  BIGINT       DEFAULT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
