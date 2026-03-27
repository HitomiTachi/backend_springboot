package com.example.webdienthoai.dto;

import jakarta.validation.constraints.NotBlank;
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
    @NotBlank(message = "Tên sản phẩm không được để trống")
    private String name;

    @NotBlank(message = "Slug không được để trống")
    private String slug;

    private String description;

    private String image;

    @NotNull(message = "Giá không được để trống")
    private BigDecimal price;

    @NotNull(message = "Danh mục không được để trống")
    private Long categoryId;

    private Integer stock;

    private Boolean featured;

    private String colors;      // JSON ["đen", "trắng", ...]

    private String storageOptions;  // JSON [{"capacity": "256GB", "price": 19900000}, ...]

    private String specifications;  // JSON thông số kỹ thuật
}
