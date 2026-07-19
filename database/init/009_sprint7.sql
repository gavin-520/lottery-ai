-- Sprint 7: sync retry lineage, ops metadata

ALTER TABLE data_sync_log
    ADD COLUMN parent_log_id BIGINT DEFAULT NULL AFTER http_status,
    ADD INDEX idx_sync_parent (parent_log_id);
