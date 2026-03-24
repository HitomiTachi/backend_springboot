package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.AddressDto;
import com.example.webdienthoai.dto.CreateAddressRequest;
import com.example.webdienthoai.dto.UpdateAddressRequest;
import com.example.webdienthoai.entity.Address;
import com.example.webdienthoai.repository.AddressRepository;
import com.example.webdienthoai.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressController {

    private static final int MAX_ADDRESSES_PER_USER = 10;

    private final AddressRepository addressRepository;

    private static String normalizeLabel(String label) {
        if (label == null || label.isBlank()) {
            return "Other";
        }
        String t = label.trim();
        if ("Home".equalsIgnoreCase(t)) {
            return "Home";
        }
        if ("Office".equalsIgnoreCase(t)) {
            return "Office";
        }
        return "Other";
    }

    @GetMapping
    public ResponseEntity<?> getAddresses(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<AddressDto> list = addressRepository.findByUserIdOrderByIdDesc(principal.getUserId()).stream()
                .map(AddressDto::fromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }

    @PostMapping
    public ResponseEntity<?> createAddress(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateAddressRequest req) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (addressRepository.countByUserId(principal.getUserId()) >= MAX_ADDRESSES_PER_USER) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Bạn chỉ có thể lưu tối đa " + MAX_ADDRESSES_PER_USER + " địa chỉ"));
        }
        if (Boolean.TRUE.equals(req.getIsDefault())) {
            addressRepository.findByUserIdOrderByIdDesc(principal.getUserId()).forEach(a -> {
                a.setIsDefault(false);
                addressRepository.save(a);
            });
        }
        Address address = Address.builder()
                .userId(principal.getUserId())
                .name(req.getName())
                .phone(req.getPhone())
                .street(req.getStreet())
                .line2(req.getApartment() != null && !req.getApartment().isBlank() ? req.getApartment().trim() : null)
                .label(normalizeLabel(req.getLabel()))
                .city(req.getCity())
                .state(req.getState())
                .zipCode(req.getZipCode())
                .country(req.getCountry() != null ? req.getCountry() : "Vietnam")
                .isDefault(Boolean.TRUE.equals(req.getIsDefault()))
                .build();
        address = addressRepository.save(address);
        return ResponseEntity.status(HttpStatus.CREATED).body(AddressDto.fromEntity(address));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateAddress(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestBody UpdateAddressRequest req) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Address address = addressRepository.findById(id).orElse(null);
        if (address == null || !address.getUserId().equals(principal.getUserId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (req.getName() != null) {
            address.setName(req.getName());
        }
        if (req.getPhone() != null) {
            address.setPhone(req.getPhone());
        }
        if (req.getStreet() != null) {
            address.setStreet(req.getStreet());
        }
        if (req.getApartment() != null) {
            address.setLine2(req.getApartment().isBlank() ? null : req.getApartment().trim());
        }
        if (req.getLabel() != null) {
            address.setLabel(normalizeLabel(req.getLabel()));
        }
        if (req.getCity() != null) {
            address.setCity(req.getCity());
        }
        if (req.getState() != null) {
            address.setState(req.getState());
        }
        if (req.getZipCode() != null) {
            address.setZipCode(req.getZipCode());
        }
        if (req.getCountry() != null) {
            address.setCountry(req.getCountry());
        }
        if (req.getIsDefault() != null) {
            if (Boolean.TRUE.equals(req.getIsDefault())) {
                addressRepository.findByUserIdOrderByIdDesc(principal.getUserId()).forEach(a -> {
                    a.setIsDefault(false);
                    addressRepository.save(a);
                });
            }
            address.setIsDefault(req.getIsDefault());
        }
        address = addressRepository.save(address);
        return ResponseEntity.ok(AddressDto.fromEntity(address));
    }

    @PutMapping("/{id}/set-default")
    public ResponseEntity<?> setDefaultAddress(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Address address = addressRepository.findById(id).orElse(null);
        if (address == null || !address.getUserId().equals(principal.getUserId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        addressRepository.findByUserIdOrderByIdDesc(principal.getUserId()).forEach(a -> {
            a.setIsDefault(false);
            addressRepository.save(a);
        });
        address.setIsDefault(true);
        address = addressRepository.save(address);
        return ResponseEntity.ok(AddressDto.fromEntity(address));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAddress(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Address address = addressRepository.findById(id).orElse(null);
        if (address == null || !address.getUserId().equals(principal.getUserId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        addressRepository.delete(address);
        return ResponseEntity.ok(Map.of("message", "Đã xóa địa chỉ"));
    }
}
