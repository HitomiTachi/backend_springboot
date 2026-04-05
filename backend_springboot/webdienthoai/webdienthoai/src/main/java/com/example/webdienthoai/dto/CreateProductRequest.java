package com.example.webdienthoai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateProductRequest {
    @jakarta.validation.constraints.NotBlank(message = "Tên sản phẩm không được để trống")
    private String name;

    /**
     * Hiện tại FE/BE chưa lưu `slug` trong entity `Product`, nên chỉ coi là optional (chống lỗi 400 validation).
     * Nếu sau này thêm cột `slug` + migration thì có thể đưa về bắt buộc.
     */
    private String slug;

    private String description;

    private String image;

    @NotNull(message = "Giá không được để trống")
    private BigDecimal price;

    @NotNull(message = "Danh mục không được để trống")
    private Long categoryId;

    private Integer stock;

    private Boolean featured;

    /** JSON: [{ "name": "Đen", "hex": "#1d1d1f" }, ...] hoặc legacy ["đen","trắng"] (FE map sang name + hex mặc định) */
    private String colors;

    /** JSON: ["128GB","256GB"] hoặc legacy [{ "capacity": "256GB", "price": ... }] */
    private String storageOptions;

    /** JSON object thông số kỹ thuật (sections giống mock trên ProductDetail) */
    private String specifications;
}
