package com.example.webdienthoai.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateReturnRequest {
    private String reason;
    private BigDecimal refundAmount;
    private String note;
}

