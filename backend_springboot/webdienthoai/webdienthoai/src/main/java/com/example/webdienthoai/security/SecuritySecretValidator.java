package com.example.webdienthoai.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SecuritySecretValidator {

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @PostConstruct
    void validateJwtSecret() {
        // Keep dev/test easy to boot while enforcing strict production requirements.
        boolean strictProfile = "prod".equalsIgnoreCase(activeProfile) || "production".equalsIgnoreCase(activeProfile);
        if (!strictProfile) {
            return;
        }
        if (!StringUtils.hasText(jwtSecret) || jwtSecret.trim().length() < 32) {
            throw new IllegalStateException("JWT_SECRET phải có ít nhất 32 ký tự khi chạy profile production.");
        }
    }
}
