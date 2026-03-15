package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.AuthRequest;
import com.example.webdienthoai.dto.AuthResponse;
import com.example.webdienthoai.dto.ChangePasswordRequest;
import com.example.webdienthoai.dto.RegisterRequest;
import com.example.webdienthoai.dto.UserDto;
import com.example.webdienthoai.entity.User;
import com.example.webdienthoai.repository.UserRepository;
import com.example.webdienthoai.security.JwtUtil;
import com.example.webdienthoai.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * Đăng ký tài khoản mới. Email được lưu dạng chữ thường để trùng với login.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        String email = req.getEmail() != null ? req.getEmail().trim().toLowerCase() : "";
        String name = req.getName() != null ? req.getName().trim() : "";
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(java.util.Map.of("message", "Email đã được sử dụng"));
        }
        User user = User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(req.getPassword()))
                .passwordChangedAt(Instant.now())
                .build();
        user = userRepository.save(user);
        String token = jwtUtil.generateToken(user.getEmail(), user.getId());
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
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(java.util.Map.of("message", "Email hoặc mật khẩu không đúng"));
        }
        String token = jwtUtil.generateToken(user.getEmail(), user.getId());
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
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return userRepository.findById(principal.getUserId())
                .map(UserDto::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    /**
     * Đổi mật khẩu. Body: currentPassword, newPassword.
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest req) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userRepository.findById(principal.getUserId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(java.util.Map.of("message", "Mật khẩu hiện tại không đúng"));
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);
        return ResponseEntity.ok(java.util.Map.of("message", "Đã đổi mật khẩu thành công", "passwordChangedAt", user.getPasswordChangedAt() != null ? user.getPasswordChangedAt().toString() : ""));
    }
}
