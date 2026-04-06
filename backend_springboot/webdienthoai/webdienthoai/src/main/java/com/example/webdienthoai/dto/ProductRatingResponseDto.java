package com.example.webdienthoai.dto;

import com.example.webdienthoai.entity.ProductRating;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRatingResponseDto {
    private Long id;
    private Long orderItemId;
    private Long productId;
    private Integer rating;
    private Instant createdAt;
    private Instant updatedAt;

    public static ProductRatingResponseDto fromEntity(ProductRating r) {
        if (r == null) return null;
        return ProductRatingResponseDto.builder()
                .id(r.getId())
                .orderItemId(r.getOrderItem() != null ? r.getOrderItem().getId() : null)
                .productId(r.getProduct() != null ? r.getProduct().getId() : null)
                .rating(r.getRating())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
