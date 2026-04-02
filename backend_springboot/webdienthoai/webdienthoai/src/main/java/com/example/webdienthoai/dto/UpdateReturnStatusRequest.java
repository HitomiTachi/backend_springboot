package com.example.webdienthoai.dto;

import lombok.Data;

@Data
public class UpdateReturnStatusRequest {
    private String status; // approved | rejected | refunded
    private String note;
}

