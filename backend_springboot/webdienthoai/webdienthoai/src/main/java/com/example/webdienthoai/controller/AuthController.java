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
import com.example.webdienthoai.service.EmailVerificationService;
import com.example.webdienthoai.service.GoogleOAuthService;
import com.example.webdienthoai.service.PasswordResetService;
import com.example.webdienthoai.service.RegistrationEmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;
    private final PasswordResetService passwordResetService;
    private final RegistrationEmailService registrationEmailService;
    private final EmailVerificationService emailVerificationService;
    private final GoogleOAuthService googleOAuthService;

    @Value("${app.auth.expose-reset-token:false}")
    private boolean exposeResetTokenInResponse;

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
                .authProvider("LOCAL")
                .passwordChangedAt(Instant.now())
                .build();
        user = userRepository.save(user);
        registrationEmailService.sendAfterRegistration(user);
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
            if (user != null && user.getAuthProvider() != null
                    && "GOOGLE".equalsIgnoreCase(user.getAuthProvider())) {
                throw new BadCredentialsException(
                        "Tài khoản này liên kết Google — mật khẩu form không dùng được. "
                                + "Hãy đăng nhập bằng Google hoặc dùng «Quên mật khẩu» để đặt mật khẩu.");
            }
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
     * Bắt đầu OAuth Google: backend tạo state và redirect tới Google.
     */
    @GetMapping("/google")
    public ResponseEntity<Void> googleLogin(
            @RequestParam(value = "redirectUri", required = false) String redirectUri) {
        java.net.URI location = googleOAuthService.buildGoogleAuthorizationRedirect(redirectUri);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location.toString())
                .build();
    }

    /**
     * Callback từ Google: đổi code lấy profile, login/register user và redirect về frontend với token.
     */
    @GetMapping("/google/callback")
    public ResponseEntity<Void> googleCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDescription) {
        java.net.URI location;
        if (error != null && !error.isBlank()) {
            String message = (errorDescription != null && !errorDescription.isBlank()) ? errorDescription : error;
            location = googleOAuthService.buildFailureRedirect(state, message);
        } else {
            try {
                location = googleOAuthService.handleGoogleCallback(code, state);
            } catch (ResponseStatusException ex) {
                location = googleOAuthService.buildFailureRedirect(state, ex.getReason() != null ? ex.getReason() : "Đăng nhập Google thất bại");
            }
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location.toString())
                .build();
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
        String genericMessage = "Nếu email tồn tại, hướng dẫn đặt lại mật khẩu sẽ được gửi";
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.ok(Map.of("message", genericMessage, "accepted", true));
        }
        String plainToken = passwordResetService.createTokenSendEmail(user);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", genericMessage);
        body.put("accepted", true);
        if (exposeResetTokenInResponse) {
            body.put("resetToken", plainToken);
            body.put("expiresInSeconds", PasswordResetService.RESET_TOKEN_TTL_SECONDS);
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        passwordResetService.resetPasswordWithToken(req.getToken(), req.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Đặt lại mật khẩu thành công"));
    }

    /**
     * Xác minh email qua token trong mail. GET ?token=
     */
    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam("token") String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Thiếu token xác minh");
        }
        emailVerificationService.verifyByToken(token.trim());
        return ResponseEntity.ok(Map.of("verified", true, "message", "Email đã được xác minh"));
    }

    /**
     * Gửi lại mail xác minh (cần đăng nhập).
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new AuthUnauthorizedException("Chưa đăng nhập");
        }
        emailVerificationService.resendForUserId(principal.getUserId());
        return ResponseEntity.ok(Map.of("message", "Đã gửi lại email xác minh nếu tài khoản chưa xác minh"));
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
