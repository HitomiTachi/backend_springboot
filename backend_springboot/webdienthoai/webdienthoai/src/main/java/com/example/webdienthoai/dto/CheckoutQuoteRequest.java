package com.example.webdienthoai.dto;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class CheckoutQuoteRequest {
    @Valid
    private List<OrderItemRequest> items;

    private String couponCode;
}
