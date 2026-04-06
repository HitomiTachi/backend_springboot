CREATE TABLE IF NOT EXISTS product_price_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    old_price DECIMAL(12, 2) NULL,
    new_price DECIMAL(12, 2) NOT NULL,
    actor VARCHAR(200) NOT NULL,
    changed_at DATETIME(6) NOT NULL
) ENGINE=InnoDB;

CREATE INDEX IF NOT EXISTS idx_product_price_audit_product_id ON product_price_audit (product_id);
CREATE INDEX IF NOT EXISTS idx_product_price_audit_changed_at ON product_price_audit (changed_at);
