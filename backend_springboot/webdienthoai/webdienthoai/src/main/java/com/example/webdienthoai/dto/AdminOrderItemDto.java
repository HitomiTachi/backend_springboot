package com.example.webdienthoai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminOrderItemDto {
    private Long productId;
    private String productName;
    private String productImage;
    private Integer quantity;
    private BigDecimal priceAtOrder;
    private BigDecimal lineTotal;
    private String selectedColor;
    private String selectedStorage;
}

