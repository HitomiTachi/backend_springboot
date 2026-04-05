-- Màu sắc, dung lượng, thông số kỹ thuật (chuỗi JSON) cho sản phẩm
ALTER TABLE products
    ADD COLUMN colors TEXT NULL,
    ADD COLUMN storage_options TEXT NULL,
    ADD COLUMN specifications TEXT NULL;
