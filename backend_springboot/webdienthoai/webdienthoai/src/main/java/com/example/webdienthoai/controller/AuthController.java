package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.AuthRequest;
import com.example.webdienthoai.dto.AuthResponse;
import com.example.webdienthoai.dto.ChangePasswordRequest;
import com.example.webdienthoai.dto.ForgotPasswordRequest;
import com.example.webdienthoai.dto.RegisterRequest;
import com.example.webdienthoai.dto.ResetPasswordRequest;
import com.example.webdienthoai.dto.UserDto;
import com.example.webdienthoai.exception.AuthLockedException;
import com.example.webdienthoai.exception.AuthUnauthorizedException;
import com.example.webdienthoai.exception.DuplicateEmailException;
import com.example.webdienthoai.entity.User;
import com.example.webdienthoai.repository.UserRepository;
import com.example.webdienthoai.security.LoginAttemptService;
import com.example.webdienthoai.security.JwtUtil;
import com.example.webdienthoai.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;
    private static final long RESET_TOKEN_TTL_SECONDS = 15 * 60;
    private static final Map<String, ResetTokenData> RESET_TOKENS = new ConcurrentHashMap<>();

    private record ResetTokenData(Long userId, Instant expiresAt) {}

    /**
     * Đăng ký tài khoản mới. Email được lưu dạng chữ thường để trùng với login.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        String email = req.getEmail() != null ? req.getEmail().trim().toLowerCase() : "";
        String name = req.getName() != null ? req.getName().trim() : "";
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("Email đã được sử dụng");
        }
        User user = User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(req.getPassword()))
            .role("customer")
                .passwordChangedAt(Instant.now())
                .build();
        user = userRepository.save(user);
        String token = jwtUtil.generateToken(user.getEmail(), user.getId(), user.getRole());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AuthResponse.builder()
                        .token(token)
                        .user(UserDto.fromEntity(user))
                        .build());
    }

    /**
     * Đăng nhập. Email so khớp không phân biệt hoa thường.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest req) {
        String email = req.getEmail() != null ? req.getEmail().trim().toLowerCase() : "";
        if (loginAttemptService.isLocked(email)) {
            long seconds = loginAttemptService.remainingLockSeconds(email);
            throw new AuthLockedException(
                    "Tài khoản tạm thời bị khóa do đăng nhập sai nhiều lần",
                    seconds
            );
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            loginAttemptService.onFailure(email);
            throw new BadCredentialsException("Email hoặc mật khẩu không đúng");
        }
        loginAttemptService.onSuccess(email);
        String token = jwtUtil.generateToken(user.getEmail(), user.getId(), user.getRole());
        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .user(UserDto.fromEntity(user))
                .build());
    }

    /**
     * Lấy thông tin user hiện tại từ JWT. Cần gửi header: Authorization: Bearer &lt;token&gt;
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new AuthUnauthorizedException("Chưa đăng nhập");
        }
        return userRepository.findById(principal.getUserId())
                .map(UserDto::fromEntity)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new AuthUnauthorizedException("Phiên đăng nhập không hợp lệ"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        String email = req.getEmail() != null ? req.getEmail().trim().toLowerCase() : "";
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.ok(Map.of(
                    "message", "Nếu email tồn tại, hướng dẫn đặt lại mật khẩu sẽ được gửi",
                    "accepted", true
            ));
        }
        String token = UUID.randomUUID().toString();
        RESET_TOKENS.put(token, new ResetTokenData(user.getId(), Instant.now().plusSeconds(RESET_TOKEN_TTL_SECONDS)));
        return ResponseEntity.ok(Map.of(
                "message", "Tạo yêu cầu đặt lại mật khẩu thành công",
                "accepted", true,
                "resetToken", token,
                "expiresInSeconds", RESET_TOKEN_TTL_SECONDS
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        ResetTokenData tokenData = RESET_TOKENS.get(req.getToken());
        if (tokenData == null || Instant.now().isAfter(tokenData.expiresAt())) {
            if (tokenData != null) {
                RESET_TOKENS.remove(req.getToken());
            }
            throw new IllegalArgumentException("Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
        }
        User user = userRepository.findById(tokenData.userId()).orElse(null);
        if (user == null) {
            RESET_TOKENS.remove(req.getToken());
            throw new IllegalArgumentException("Yêu cầu đặt lại mật khẩu không hợp lệ");
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);
        RESET_TOKENS.remove(req.getToken());
        return ResponseEntity.ok(Map.of("message", "Đặt lại mật khẩu thành công"));
    }

    /**
     * Đổi mật khẩu. Body: currentPassword, newPassword.
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest req) {
        if (principal == null) {
            throw new AuthUnauthorizedException("Chưa đăng nhập");
        }
        User user = userRepository.findById(principal.getUserId()).orElse(null);
        if (user == null) {
            throw new AuthUnauthorizedException("Phiên đăng nhập không hợp lệ");
        }
        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không đúng");
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);
        return ResponseEntity.ok(java.util.Map.of("message", "Đã đổi mật khẩu thành công", "passwordChangedAt", user.getPasswordChangedAt() != null ? user.getPasswordChangedAt().toString() : ""));
    }
}
