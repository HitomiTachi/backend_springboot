package com.example.webdienthoai.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class CreateCouponRequest {
    @NotBlank
    private String code;

    @NotBlank
    private String discountType; // percent | fixed

    @NotNull
    @DecimalMin("0")
    private BigDecimal discountValue;

    @DecimalMin("0")
    private BigDecimal minOrderAmount;

    @DecimalMin("0")
    private BigDecimal maxDiscountAmount;

    private Boolean active;
    private Instant startsAt;
    private Instant endsAt;
}
