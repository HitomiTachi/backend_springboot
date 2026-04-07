package com.example.webdienthoai.service;

import com.example.webdienthoai.config.AppMailProperties;
import com.example.webdienthoai.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Gửi mail sau đăng ký (không chặn response API).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationEmailService {

    private final AppMailProperties appMailProperties;
    private final EmailService emailService;
    private final EmailVerificationService emailVerificationService;

    @Async
    public void sendAfterRegistration(User user) {
        try {
            if (appMailProperties.isWelcomeEnabled()) {
                emailService.sendWelcomeEmail(user.getEmail(), user.getName());
            }
            if (appMailProperties.isVerificationEnabled()) {
                emailVerificationService.createTokenAndSendEmail(user);
            }
        } catch (Exception e) {
            log.warn("sendAfterRegistration lỗi cho user {}: {}", user.getId(), e.getMessage());
        }
    }
}
