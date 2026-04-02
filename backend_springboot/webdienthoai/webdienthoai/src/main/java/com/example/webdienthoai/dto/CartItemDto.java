package com.example.webdienthoai.dto;

import com.example.webdienthoai.entity.CartItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemDto {
    private Long id;
    private Long productId;
    private String productName;
    private String productImage;
    private Integer quantity;
    private String selectedColor;
    private String selectedStorage;
    private String variant;
    private BigDecimal priceAtAdd;
    private BigDecimal lineTotal;

    public static CartItemDto fromEntity(CartItem item) {
        if (item == null) return null;
        BigDecimal lineTotal = item.getPriceAtAdd().multiply(
                java.math.BigDecimal.valueOf(item.getQuantity())
        );
        String color = item.getSelectedColor() != null ? item.getSelectedColor().trim() : "";
        String storage = item.getSelectedStorage() != null ? item.getSelectedStorage().trim() : "";
        String variant;
        if (!color.isBlank() && !storage.isBlank()) {
            variant = color + " / " + storage;
        } else if (!color.isBlank()) {
            variant = color;
        } else if (!storage.isBlank()) {
            variant = storage;
        } else {
            variant = null;
        }
        return CartItemDto.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .productImage(item.getProduct().getImage())
                .quantity(item.getQuantity())
                .selectedColor(item.getSelectedColor())
                .selectedStorage(item.getSelectedStorage())
                .variant(variant)
                .priceAtAdd(item.getPriceAtAdd())
                .lineTotal(lineTotal)
                .build();
    }
}
