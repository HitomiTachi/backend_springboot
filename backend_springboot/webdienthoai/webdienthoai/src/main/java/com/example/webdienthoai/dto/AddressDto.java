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
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private String label;
    private Boolean isDefault;

    public static AddressDto fromEntity(Address a) {
        if (a == null) return null;
        return AddressDto.builder()
                .id(a.getId())
                .name(a.getName())
                .phone(a.getPhone())
                .street(a.getStreet())
                .apartment(a.getApartment())
                .city(a.getCity())
                .state(a.getState())
                .zipCode(a.getZipCode())
                .country(a.getCountry())
                .label(a.getLabel())
                .isDefault(a.getIsDefault())
                .build();
    }
}
