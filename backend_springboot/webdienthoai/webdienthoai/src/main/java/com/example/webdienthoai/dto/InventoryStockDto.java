package com.example.webdienthoai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Objects;

@Data
@Builder
public class InventoryStockDto {
    private Long productId;
    private Integer stock; // available
    private Integer reservedStock;
    private Integer availableStock;

    public static InventoryStockDto from(ProductSnapshot s) {
        if (s == null) return null;
        return InventoryStockDto.builder()
                .productId(s.productId())
                .stock(s.stock())
                .reservedStock(s.reservedStock())
                .availableStock(s.availableStock())
                .build();
    }

    public record ProductSnapshot(Long productId, Integer stock, Integer reservedStock, Integer availableStock) {
        public ProductSnapshot {
            Objects.requireNonNull(productId, "productId");
        }
    }
}

