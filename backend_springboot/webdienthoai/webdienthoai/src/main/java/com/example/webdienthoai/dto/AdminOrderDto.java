package com.example.webdienthoai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminOrderDto {
    private Long id;
    private String customerName;
    private String shippingAddressSummary;

    private List<AdminOrderItemDto> items;

    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal shippingCost;
    private BigDecimal totalPrice;

    private String paymentMethod;
    private String notes;
    private String status;

    private Instant createdAt;
}

