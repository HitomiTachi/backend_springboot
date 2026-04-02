package com.example.webdienthoai.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CheckoutQuoteResponse {
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal shippingCost;
    private BigDecimal totalPrice;
    private String couponCode;
    private boolean couponApplied;
    private String couponMessage;
}
