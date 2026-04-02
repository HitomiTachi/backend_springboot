package com.example.webdienthoai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VnpayCreatePaymentRequest {

    @NotNull
    private Long orderId;
}
