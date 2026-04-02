package com.example.webdienthoai.dto;

import com.example.webdienthoai.entity.ReturnRequest;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class ReturnRequestDto {
    private Long id;
    private Long orderId;
    private String status;
    private String reason;
    private BigDecimal refundAmount;
    private String note;
    private Boolean restocked;
    private Instant createdAt;
    private Instant updatedAt;

    public static ReturnRequestDto fromEntity(ReturnRequest rr) {
        if (rr == null) return null;
        return ReturnRequestDto.builder()
                .id(rr.getId())
                .orderId(rr.getOrderId())
                .status(rr.getStatus())
                .reason(rr.getReason())
                .refundAmount(rr.getRefundAmount())
                .note(rr.getNote())
                .restocked(rr.getRestocked())
                .createdAt(rr.getCreatedAt())
                .updatedAt(rr.getUpdatedAt())
                .build();
    }
}

