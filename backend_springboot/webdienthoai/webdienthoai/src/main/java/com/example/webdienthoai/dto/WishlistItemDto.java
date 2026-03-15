package com.example.webdienthoai.dto;

import com.example.webdienthoai.entity.Product;
import com.example.webdienthoai.entity.WishlistItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WishlistItemDto {
    private Long id;
    private String productId;
    private String name;
    private String image;
    private BigDecimal price;
    private BigDecimal oldPrice;
    private Double rating;
    private Integer reviews;

    public static WishlistItemDto fromEntity(WishlistItem wi) {
        if (wi == null) return null;
        Product p = wi.getProduct();
        return WishlistItemDto.builder()
                .id(wi.getId())
                .productId(p != null ? String.valueOf(p.getId()) : null)
                .name(p != null ? p.getName() : null)
                .image(p != null ? p.getImage() : null)
                .price(p != null ? p.getPrice() : null)
                .oldPrice(null)
                .rating(0.0)
                .reviews(0)
                .build();
    }
}
