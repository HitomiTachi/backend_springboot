package com.example.webdienthoai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateOrderRequest {
    @NotEmpty(message = "Order must have at least one item")
    @Valid
    private List<OrderItemRequest> items;

    @NotNull
    @DecimalMin("0")
    private BigDecimal totalPrice;

    private Long shippingAddressId;

    @DecimalMin("0")
    private BigDecimal subtotal;

    @DecimalMin("0")
    private BigDecimal discountAmount;

    @DecimalMin("0")
    private BigDecimal shippingCost;

    private String paymentMethod;

    private String notes;
}
