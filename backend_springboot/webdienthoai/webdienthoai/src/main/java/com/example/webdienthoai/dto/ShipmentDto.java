package com.example.webdienthoai.dto;

import com.example.webdienthoai.entity.Shipment;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ShipmentDto {
    private Long id;
    private Long orderId;
    private String carrier;
    private String trackingNumber;
    private String status;
    private Instant shippedAt;
    private Instant deliveredAt;
    private String note;

    public static ShipmentDto fromEntity(Shipment s) {
        if (s == null) return null;
        return ShipmentDto.builder()
                .id(s.getId())
                .orderId(s.getOrderId())
                .carrier(s.getCarrier())
                .trackingNumber(s.getTrackingNumber())
                .status(s.getStatus())
                .shippedAt(s.getShippedAt())
                .deliveredAt(s.getDeliveredAt())
                .note(s.getNote())
                .build();
    }
}

