package com.example.webdienthoai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAddressRequest {
    @NotBlank(message = "Tên người nhận là bắt buộc")
    private String name;
    @NotBlank(message = "Số điện thoại là bắt buộc")
    private String phone;
    @NotBlank(message = "Địa chỉ đường là bắt buộc")
    private String street;
    /** Căn hộ / suite (optional) */
    private String apartment;
    /** Home | Office | Other */
    private String label;
    @NotBlank(message = "Thành phố là bắt buộc")
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private Boolean isDefault;
}
