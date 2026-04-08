CREATE TABLE IF NOT EXISTS admin_inbox_reads (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_user_id BIGINT NOT NULL,
    audit_id BIGINT NOT NULL,
    read_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_admin_inbox_reads_user_audit (admin_user_id, audit_id),
    KEY idx_admin_inbox_reads_user (admin_user_id),
    KEY idx_admin_inbox_reads_audit (audit_id)
);
