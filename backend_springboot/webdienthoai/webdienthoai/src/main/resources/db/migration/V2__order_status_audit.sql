CREATE TABLE IF NOT EXISTS order_status_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    old_status VARCHAR(50) NOT NULL,
    new_status VARCHAR(50) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    note VARCHAR(500),
    changed_at DATETIME(6) NOT NULL
) ENGINE=InnoDB;

CREATE INDEX IF NOT EXISTS idx_order_status_audit_order_id ON order_status_audit (order_id);
CREATE INDEX IF NOT EXISTS idx_order_status_audit_changed_at ON order_status_audit (changed_at);
