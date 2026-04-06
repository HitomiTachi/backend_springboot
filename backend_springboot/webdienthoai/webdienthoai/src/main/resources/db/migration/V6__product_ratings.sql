CREATE TABLE IF NOT EXISTS product_ratings (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    rating TINYINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_product_ratings_order_item UNIQUE (order_item_id),
    CONSTRAINT fk_product_ratings_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_ratings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_ratings_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_product_ratings_product_id ON product_ratings (product_id);
