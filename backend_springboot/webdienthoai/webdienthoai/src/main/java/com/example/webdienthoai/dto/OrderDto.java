package com.example.webdienthoai.dto;

import com.example.webdienthoai.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDto {
    private Long id;
    private Long userId;
    private List<OrderItemDto> items;
    private BigDecimal totalPrice;
    private String status;
    private Instant createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDto {
        private Long productId;
        private String productName;
        private String productImage;
        private Integer quantity;
        private BigDecimal priceAtOrder;
    }

    public static OrderDto fromEntity(Order o) {
        if (o == null) return null;
        List<OrderItemDto> itemDtos = o.getItems().stream()
                .map(item -> new OrderItemDto(
                        item.getProduct().getId(),
                        item.getProduct().getName(),
                        item.getProduct().getImage(),
                        item.getQuantity(),
                        item.getPriceAtOrder()))
                .collect(Collectors.toList());
        return OrderDto.builder()
                .id(o.getId())
                .userId(o.getUserId())
                .items(itemDtos)
                .totalPrice(o.getTotalPrice())
                .status(o.getStatus())
                .createdAt(o.getCreatedAt())
                .build();
    }
}
