package com.example.webdienthoai.dto;

import com.example.webdienthoai.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    private Long id;
    private String name;
    private String email;
    private String role;
    private String authProvider;
    private String providerId;
    private String phone;
    private String gender;
    private LocalDate dateOfBirth;
    private String avatarUrl;
    private AddressDto defaultAddress;
    private Instant passwordChangedAt;
    private Instant emailVerifiedAt;
    private Instant createdAt;

    public static UserDto fromEntity(User u) {
        if (u == null) return null;
        return UserDto.builder()
                .id(u.getId())
                .name(u.getName())
                .email(u.getEmail())
                .role(u.getRole())
                .authProvider(u.getAuthProvider())
                .providerId(u.getProviderId())
                .phone(u.getPhone())
                .gender(u.getGender())
                .dateOfBirth(u.getDateOfBirth())
                .avatarUrl(u.getAvatarUrl())
                .passwordChangedAt(u.getPasswordChangedAt())
                .emailVerifiedAt(u.getEmailVerifiedAt())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
