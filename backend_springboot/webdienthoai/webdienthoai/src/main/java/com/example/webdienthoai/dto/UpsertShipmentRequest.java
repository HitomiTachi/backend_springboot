package com.example.webdienthoai.dto;

import lombok.Data;

@Data
public class UpsertShipmentRequest {
    private String carrier;
    private String trackingNumber;
    private String status; // pending | shipping | delivered | failed
    private String note;
}

