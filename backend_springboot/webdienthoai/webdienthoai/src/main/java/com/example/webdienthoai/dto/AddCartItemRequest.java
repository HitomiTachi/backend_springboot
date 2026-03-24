package com.example.webdienthoai.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddCartItemRequest {

    @NotNull(message = "productId là bắt buộc")
    private Long productId;

    @Min(value = 1, message = "Số lượng phải >= 1")
    private int quantity = 1;

    /** Variant chung (FE gửi lên, ví dụ: "Space Black / 256GB") */
    private String variant;

    /** Màu cụ thể nếu FE gửi riêng */
    private String selectedColor;

    /** Dung lượng cụ thể nếu FE gửi riêng */
    private String selectedStorage;

    // Các trường FE gửi kèm nhưng BE tự lấy từ entity — bỏ qua khi lưu
    private String name;
    private BigDecimal price;
    private String image;
}
