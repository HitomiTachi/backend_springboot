package com.example.webdienthoai.dto;

import com.example.webdienthoai.entity.ProductPriceAudit;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

@Value
@Builder
public class ProductPriceAuditDto {
    Long id;
    Long productId;
    BigDecimal oldPrice;
    BigDecimal newPrice;
    String actor;
    Instant changedAt;

    public static ProductPriceAuditDto fromEntity(ProductPriceAudit e) {
        if (e == null) {
            return null;
        }
        return ProductPriceAuditDto.builder()
                .id(e.getId())
                .productId(e.getProductId())
                .oldPrice(e.getOldPrice())
                .newPrice(e.getNewPrice())
                .actor(e.getActor())
                .changedAt(e.getChangedAt())
                .build();
    }
}
