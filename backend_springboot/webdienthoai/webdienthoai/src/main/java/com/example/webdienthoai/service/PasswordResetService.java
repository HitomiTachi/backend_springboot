package com.example.webdienthoai.service;

import com.example.webdienthoai.config.AppUrlProperties;
import com.example.webdienthoai.entity.PasswordResetToken;
import com.example.webdienthoai.entity.User;
import com.example.webdienthoai.repository.PasswordResetTokenRepository;
import com.example.webdienthoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    public static final long RESET_TOKEN_TTL_SECONDS = 15 * 60;

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AppUrlProperties appUrlProperties;

    /**
     * Xóa token chưa dùng của user, tạo token mới, lưu DB, gửi mail chứa link (HashRouter: /#/reset-password/...).
     *
     * @return token thuần (chỉ để dev/Postman khi bật expose-reset-token)
     */
    @Transactional
    public String createTokenSendEmail(User user) {
        tokenRepository.deleteUnusedByUserId(user.getId());
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(RESET_TOKEN_TTL_SECONDS);
        PasswordResetToken entity = PasswordResetToken.builder()
                .userId(user.getId())
                .token(token)
                .expiresAt(expiresAt)
                .createdAt(Instant.now())
                .build();
        tokenRepository.save(entity);
        String link = buildResetLink(token);
        emailService.sendPasswordResetEmail(user.getEmail(), link);
        return token;
    }

    @Transactional
    public void resetPasswordWithToken(String tokenPlain, String newPassword) {
        PasswordResetToken row = tokenRepository.findByToken(tokenPlain).orElse(null);
        if (row == null) {
            throw new IllegalArgumentException("Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
        }
        if (row.getUsedAt() != null) {
            throw new IllegalArgumentException("Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
        }
        if (Instant.now().isAfter(row.getExpiresAt())) {
            throw new IllegalArgumentException("Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
        }
        User user = userRepository.findById(row.getUserId()).orElse(null);
        if (user == null) {
            throw new IllegalArgumentException("Yêu cầu đặt lại mật khẩu không hợp lệ");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);
        row.setUsedAt(Instant.now());
        tokenRepository.save(row);
    }

    private String buildResetLink(String token) {
        String base = appUrlProperties.getFrontendBase().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/#/reset-password/" + token;
    }
}
