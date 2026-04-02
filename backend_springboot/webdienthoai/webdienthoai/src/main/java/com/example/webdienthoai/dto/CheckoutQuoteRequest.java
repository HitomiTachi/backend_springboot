package com.example.webdienthoai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CheckoutQuoteRequest {
    @Valid
    private List<OrderItemRequest> items;

    private String couponCode;

    @DecimalMin("0")
    private BigDecimal shippingCost;
}
