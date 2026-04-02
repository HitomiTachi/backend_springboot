package com.example.webdienthoai.dto;

import com.example.webdienthoai.entity.OrderStatusAudit;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class OrderStatusHistoryDto {
    private String oldStatus;
    private String newStatus;
    private String actor;
    private String note;
    private Instant changedAt;

    public static OrderStatusHistoryDto fromEntity(OrderStatusAudit audit) {
        if (audit == null) return null;
        return OrderStatusHistoryDto.builder()
                .oldStatus(audit.getOldStatus())
                .newStatus(audit.getNewStatus())
                .actor(audit.getActor())
                .note(audit.getNote())
                .changedAt(audit.getChangedAt())
                .build();
    }
}
