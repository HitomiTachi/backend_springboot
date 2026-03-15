package com.example.webdienthoai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAddressRequest {
    @NotBlank(message = "Tên người nhận là bắt buộc")
    private String name;
    private String phone;
    private String street;
    private String apartment;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private String label; // Home, Office, Other
    private Boolean isDefault;
}
