package com.example.webdienthoai.controller;

import com.example.webdienthoai.entity.ChatMessage;
import com.example.webdienthoai.repository.ChatMessageRepository;
import com.example.webdienthoai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Chat hỗ trợ TechHome — chỉ user đã đăng nhập (JWT).
 * Mỗi tin nhắn gửi/nhận đều được lưu vào bảng chat_messages.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ChatController {

    private static final int MAX_HISTORY = 100;

    private final ChatMessageRepository chatMessageRepository;

    /** FE kiểm tra trước khi mở widget (tránh gọi AI khi chưa cấu hình key). */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "available", true
        ));
    }

    /**
     * GET /api/chat/history?limit=50
     * Trả về lịch sử chat của user hiện tại, sắp xếp tăng dần theo thời gian.
     */
    @GetMapping("/history")
    public ResponseEntity<?> getHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "50") int limit) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        int safeLimit = Math.min(Math.max(limit, 1), MAX_HISTORY);
        List<ChatMessage> rows = chatMessageRepository
                .findTopByUserIdOrderByDesc(principal.getUserId(), PageRequest.of(0, safeLimit));
        // Đảo ngược để trả về thứ tự cũ → mới
        List<Map<String, Object>> messages = rows.reversed().stream()
                .map(m -> Map.<String, Object>of(
                        "id", m.getId(),
                        "role", m.getRole(),
                        "content", m.getContent(),
                        "sentAt", m.getSentAt().toString()
                ))
                .toList();
        return ResponseEntity.ok(Map.of("messages", messages));
    }

    /** POST /api/chat/message — gửi tin, lưu cả 2 chiều, trả về reply */
    @PostMapping("/message")
    public ResponseEntity<?> postMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String message = body != null && body.get("message") != null ? body.get("message").trim() : "";
        if (message.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Nội dung tin nhắn không được để trống"));
        }
        Instant now = Instant.now();

        // Lưu tin nhắn của user
        chatMessageRepository.save(ChatMessage.builder()
                .userId(principal.getUserId())
                .role("user")
                .content(message)
                .sentAt(now)
                .build());

        String reply = "Admin đã nhận tin nhắn của bạn. Vui lòng chờ phản hồi.";

        // Lưu bản ghi phản hồi tự động để user thấy trạng thái đã tiếp nhận
        chatMessageRepository.save(ChatMessage.builder()
                .userId(principal.getUserId())
                .role("assistant")
                .content(reply)
                .sentAt(Instant.now())
                .build());

        return ResponseEntity.ok(Map.of("reply", reply));
    }
}
