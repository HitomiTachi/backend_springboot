package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.UpdateProfileRequest;
import com.example.webdienthoai.dto.UserDto;
import com.example.webdienthoai.entity.User;
import com.example.webdienthoai.repository.AddressRepository;
import com.example.webdienthoai.repository.UserRepository;
import com.example.webdienthoai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private static final String PROVIDER_GOOGLE = "GOOGLE";
    private static final String PROVIDER_LOCAL = "LOCAL";

    @GetMapping
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return userRepository.findById(principal.getUserId())
                .map(user -> {
                    UserDto dto = UserDto.fromEntity(user);
                    addressRepository.findFirstByUserIdAndIsDefaultTrue(user.getId())
                            .ifPresent(address -> dto.setDefaultAddress(com.example.webdienthoai.dto.AddressDto.fromEntity(address)));
                    return dto;
                })
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PatchMapping
    public ResponseEntity<?> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody UpdateProfileRequest req) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userRepository.findById(principal.getUserId())
                .orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (req.getName() != null && !req.getName().isBlank()) {
            user.setName(req.getName().trim());
        }
        if (req.getPhone() != null) {
            user.setPhone(req.getPhone().trim().isEmpty() ? null : req.getPhone().trim());
        }
        if (req.getGender() != null) {
            user.setGender(req.getGender().trim().isEmpty() ? null : req.getGender().trim());
        }
        if (req.getDateOfBirth() != null) {
            user.setDateOfBirth(req.getDateOfBirth());
        }
        if (req.getAvatarUrl() != null) {
            String v = req.getAvatarUrl().trim();
            user.setAvatarUrl(v.isEmpty() ? null : v);
        }
        user = userRepository.save(user);
        UserDto dto = UserDto.fromEntity(user);
        addressRepository.findFirstByUserIdAndIsDefaultTrue(user.getId())
                .ifPresent(address -> dto.setDefaultAddress(com.example.webdienthoai.dto.AddressDto.fromEntity(address)));
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/linked-accounts/google/unlink")
    public ResponseEntity<?> unlinkGoogle(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userRepository.findById(principal.getUserId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!PROVIDER_GOOGLE.equalsIgnoreCase(user.getAuthProvider())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tài khoản chưa liên kết Google"));
        }
        user.setAuthProvider(PROVIDER_LOCAL);
        user.setProviderId(null);
        user = userRepository.save(user);

        UserDto dto = UserDto.fromEntity(user);
        addressRepository.findFirstByUserIdAndIsDefaultTrue(user.getId())
                .ifPresent(address -> dto.setDefaultAddress(com.example.webdienthoai.dto.AddressDto.fromEntity(address)));
        return ResponseEntity.ok(dto);
    }
}
