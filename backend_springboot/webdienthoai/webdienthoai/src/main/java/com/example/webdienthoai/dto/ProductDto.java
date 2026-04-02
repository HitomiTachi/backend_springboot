package com.example.webdienthoai.dto;

import com.example.webdienthoai.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDto {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private String image;
    private BigDecimal price;
    private Long categoryId;
    private String categoryName;
    private Integer stock;
    private Boolean featured;

    public static ProductDto fromEntity(Product p) {
        if (p == null) return null;
        return ProductDto.builder()
                .id(p.getId())
                .name(p.getName())
                .slug(p.getSlug())
                .description(p.getDescription())
                .image(p.getImage())
                .price(p.getPrice())
                .categoryId(p.getCategoryId())
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                .stock(p.getStock())
                .featured(p.getFeatured() != null && p.getFeatured())
                .build();
    }
}
