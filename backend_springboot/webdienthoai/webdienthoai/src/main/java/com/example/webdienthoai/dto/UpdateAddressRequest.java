package com.example.webdienthoai.dto;

import lombok.Data;

@Data
public class UpdateAddressRequest {
    private String name;
    private String phone;
    private String street;
    private String apartment;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private String label;
    private Boolean isDefault;
}
