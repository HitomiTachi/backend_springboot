package com.example.webdienthoai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateAdminOrderStatusRequest {
    @NotBlank(message = "status is required")
    private String status;
}

