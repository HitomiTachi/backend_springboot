package com.example.webdienthoai.service;

import com.example.webdienthoai.entity.Product;

import java.util.Optional;

/**
 * Service lấy thông số kỹ thuật điện thoại từ API bên ngoài (Zyla Labs, Juhe, Apify GSMArena).
 * Cấu hình qua application.properties; nếu không set API key thì chỉ trả về specs đã lưu trong DB.
 */
public interface PhoneSpecsService {

    /**
     * Lấy thông số từ API ngoài (nếu có cấu hình) hoặc từ product.getSpecifications().
     * @return JSON string thông số, hoặc empty nếu không có.
     */
    Optional<String> fetchSpecsForProduct(Product product);

    /**
     * Gọi API ngoài theo tên sản phẩm, cập nhật vào product và lưu.
     * @return true nếu cập nhật thành công.
     */
    boolean fetchAndSaveSpecs(Long productId);
}
