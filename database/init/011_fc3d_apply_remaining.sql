-- Remaining FC3D migration (safe if lottery_type columns already exist)

CREATE TABLE IF NOT EXISTS fc3d_draw_record (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    issue             VARCHAR(32)  NOT NULL UNIQUE,
    digit1            TINYINT      NOT NULL,
    digit2            TINYINT      NOT NULL,
    digit3            TINYINT      NOT NULL,
    sum_value         INT          NOT NULL,
    span_value        INT          NOT NULL,
    odd_even_pattern  VARCHAR(16)  NOT NULL,
    draw_date         DATE         DEFAULT NULL,
    lottery_type      VARCHAR(16)  NOT NULL DEFAULT 'FC3D',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_fc3d_draw_date (draw_date),
    INDEX idx_fc3d_sum (sum_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO fc3d_draw_record (issue, digit1, digit2, digit3, sum_value, span_value, odd_even_pattern, draw_date) VALUES
('2026001', 1, 2, 3, 6, 2, 'OEO', '2026-01-01'),
('2026002', 0, 5, 8, 13, 8, 'EOE', '2026-01-02'),
('2026003', 4, 4, 7, 15, 3, 'EEO', '2026-01-03'),
('2026004', 9, 1, 6, 16, 8, 'OOE', '2026-01-04'),
('2026005', 2, 8, 0, 10, 8, 'EEE', '2026-01-05'),
('2026006', 3, 5, 9, 17, 6, 'OOO', '2026-01-06'),
('2026007', 7, 0, 4, 11, 7, 'OEE', '2026-01-07'),
('2026008', 6, 3, 1, 10, 5, 'EOO', '2026-01-08'),
('2026009', 5, 5, 5, 15, 0, 'OOO', '2026-01-09'),
('2026010', 8, 2, 9, 19, 7, 'EEO', '2026-01-10'),
('2026011', 1, 7, 2, 10, 6, 'OOE', '2026-01-11'),
('2026012', 0, 0, 6, 6, 6, 'EEE', '2026-01-12'),
('2026013', 9, 4, 3, 16, 6, 'OEO', '2026-01-13'),
('2026014', 2, 6, 8, 16, 6, 'EEE', '2026-01-14'),
('2026015', 4, 1, 0, 5, 4, 'EOE', '2026-01-15'),
('2026016', 3, 9, 7, 19, 6, 'OOO', '2026-01-16'),
('2026017', 5, 2, 4, 11, 3, 'OEE', '2026-01-17'),
('2026018', 8, 8, 1, 17, 7, 'EEO', '2026-01-18'),
('2026019', 6, 0, 5, 11, 6, 'EEO', '2026-01-19'),
('2026020', 7, 3, 2, 12, 5, 'OOE', '2026-01-20')
ON DUPLICATE KEY UPDATE issue = VALUES(issue);
