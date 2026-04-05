package com.example.webdienthoai.dto;

import com.example.webdienthoai.entity.Order;
import com.example.webdienthoai.entity.Shipment;
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
    private Long shippingAddressId;
    private List<OrderItemDto> items;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal shippingCost;
    private BigDecimal totalPrice;
    private String paymentMethod;
    private String notes;
    /** Mã giảm giá đã áp dụng (nếu có). */
    private String couponCode;
    private String status;
    private Instant createdAt;
    /** Thông tin vận chuyển (3PL); null nếu chưa tạo. */
    private ShipmentDto shipment;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDto {
        private Long productId;
        private String productName;
        private String productImage;
        private Integer quantity;
        private BigDecimal priceAtOrder;
        private BigDecimal lineTotal;
        private String selectedColor;
        private String selectedStorage;
    }

    public static OrderDto fromEntity(Order o) {
        return fromEntity(o, null);
    }

    public static OrderDto fromEntity(Order o, Shipment shipment) {
        if (o == null) return null;
        List<OrderItemDto> itemDtos = o.getItems().stream()
                .map(item -> new OrderItemDto(
                item.getProduct() != null ? item.getProduct().getId() : null,
                item.getProductName() != null ? item.getProductName() : (item.getProduct() != null ? item.getProduct().getName() : null),
                item.getProductImage() != null ? item.getProductImage() : (item.getProduct() != null ? item.getProduct().getImage() : null),
                        item.getQuantity(),
                item.getPriceAtOrder(),
                item.getLineTotal(),
                item.getSelectedColor(),
                item.getSelectedStorage()))
                .collect(Collectors.toList());
        return OrderDto.builder()
                .id(o.getId())
                .userId(o.getUserId())
            .shippingAddressId(o.getShippingAddressId())
                .items(itemDtos)
            .subtotal(o.getSubtotal())
            .discountAmount(o.getDiscountAmount())
            .shippingCost(o.getShippingCost())
                .totalPrice(o.getTotalPrice())
            .paymentMethod(o.getPaymentMethod())
            .notes(o.getNotes())
                .couponCode(o.getCouponCode())
                .status(o.getStatus())
                .createdAt(o.getCreatedAt())
                .shipment(ShipmentDto.fromEntity(shipment))
                .build();
    }
}
