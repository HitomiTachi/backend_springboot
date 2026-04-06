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
    /** JSON string */
    private String colors;
    /** JSON string */
    private String storageOptions;
    /** JSON string */
    private String specifications;

    /** Điểm trung bình sao (0 nếu chưa có đánh giá). */
    private Double rating;
    /** Số lượt đánh giá. */
    private Integer reviews;

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
                .colors(p.getColors())
                .storageOptions(p.getStorageOptions())
                .specifications(p.getSpecifications())
                .rating(0.0)
                .reviews(0)
                .build();
    }

    public static ProductDto fromEntity(Product p, ProductRatingSummary summary) {
        ProductDto dto = fromEntity(p);
        if (dto == null) {
            return null;
        }
        if (summary != null && summary.count() > 0) {
            dto.setRating(summary.average());
            long c = summary.count();
            dto.setReviews(c > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) c);
        }
        return dto;
    }
}
