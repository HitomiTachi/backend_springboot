package com.example.webdienthoai.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class AdminInboxItemDto {
    private Long auditId;
    private Long orderId;
    private String customerName;
    private String oldStatus;
    private String newStatus;
    private String actor;
    private String note;
    private Instant changedAt;
    private boolean read;
}
