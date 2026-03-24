package com.example.webdienthoai.dto;

import com.example.webdienthoai.entity.Address;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressDto {
    private Long id;
    private String name;
    private String phone;
    private String street;
    private String apartment;
    private String label;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private Boolean isDefault;

    public static AddressDto fromEntity(Address a) {
        if (a == null) return null;
        return AddressDto.builder()
                .id(a.getId())
                .name(a.getName())
                .phone(a.getPhone())
                .street(a.getStreet())
                .apartment(a.getLine2())
                .label(a.getLabel())
                .city(a.getCity())
                .state(a.getState())
                .zipCode(a.getZipCode())
                .country(a.getCountry())
                .isDefault(a.getIsDefault())
                .build();
    }
}
