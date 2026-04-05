package com.example.webdienthoai.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemRequest {
    @NotNull
    private Long productId;
    @Min(1)
    private Integer quantity;
    @NotNull
    private BigDecimal price;

    private String selectedColor;

    private String selectedStorage;
}
