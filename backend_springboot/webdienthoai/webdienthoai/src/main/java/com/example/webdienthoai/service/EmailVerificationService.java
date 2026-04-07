package com.example.webdienthoai.service;

import com.example.webdienthoai.config.AppMailProperties;
import com.example.webdienthoai.config.AppUrlProperties;
import com.example.webdienthoai.entity.EmailVerificationToken;
import com.example.webdienthoai.entity.User;
import com.example.webdienthoai.repository.EmailVerificationTokenRepository;
import com.example.webdienthoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    public static final long VERIFICATION_TOKEN_TTL_SECONDS = 48 * 60 * 60;

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AppMailProperties appMailProperties;
    private final AppUrlProperties appUrlProperties;

    @Transactional
    public void createTokenAndSendEmail(User user) {
        if (!appMailProperties.isVerificationEnabled()) {
            return;
        }
        if (user.getEmailVerifiedAt() != null) {
            return;
        }
        tokenRepository.deleteUnusedByUserId(user.getId());
        String token = UUID.randomUUID().toString();
        Instant exp = Instant.now().plusSeconds(VERIFICATION_TOKEN_TTL_SECONDS);
        EmailVerificationToken row = EmailVerificationToken.builder()
                .userId(user.getId())
                .token(token)
                .expiresAt(exp)
                .createdAt(Instant.now())
                .build();
        tokenRepository.save(row);
        String link = buildVerifyLink(token);
        emailService.sendEmailVerification(user.getEmail(), link);
    }

    @Transactional
    public void verifyByToken(String tokenPlain) {
        EmailVerificationToken row = tokenRepository.findByToken(tokenPlain).orElse(null);
        if (row == null || row.getUsedAt() != null || Instant.now().isAfter(row.getExpiresAt())) {
            throw new IllegalArgumentException("Liên kết xác minh không hợp lệ hoặc đã hết hạn");
        }
        User user = userRepository.findById(row.getUserId()).orElse(null);
        if (user == null) {
            throw new IllegalArgumentException("Tài khoản không tồn tại");
        }
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);
        row.setUsedAt(Instant.now());
        tokenRepository.save(row);
    }

    @Transactional
    public void resendForUserId(Long userId) {
        if (!appMailProperties.isVerificationEnabled()) {
            throw new IllegalArgumentException("Xác minh email đang tắt");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
        if (user.getEmailVerifiedAt() != null) {
            throw new IllegalArgumentException("Email đã được xác minh");
        }
        createTokenAndSendEmail(user);
    }

    private String buildVerifyLink(String token) {
        String base = appUrlProperties.getFrontendBase().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/#/verify-email?token=" + token;
    }
}
