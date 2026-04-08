package com.example.webdienthoai.controller;

import com.example.webdienthoai.entity.ChatMessage;
import com.example.webdienthoai.entity.User;
import com.example.webdienthoai.repository.ChatMessageRepository;
import com.example.webdienthoai.repository.UserRepository;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/chat")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminChatController {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    @GetMapping("/conversations")
    public ResponseEntity<?> getConversations(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "50") int limit
    ) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        int safeLimit = Math.min(Math.max(limit, 1), 200);

        List<Long> userIds = chatMessageRepository.findConversationUserIds(PageRequest.of(0, safeLimit));
        if (userIds.isEmpty()) return ResponseEntity.ok(Map.of("items", List.of()));

        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> items = userIds.stream().map(userId -> {
            User user = userMap.get(userId);
            ChatMessage last = chatMessageRepository.findTopByUserIdOrderBySentAtDesc(userId).orElse(null);
            return Map.<String, Object>of(
                    "userId", userId,
                    "userName", user != null && user.getName() != null ? user.getName() : "Khách hàng",
                    "userEmail", user != null && user.getEmail() != null ? user.getEmail() : "",
                    "lastMessage", last != null ? last.getContent() : "",
                    "lastRole", last != null ? last.getRole() : "user",
                    "lastSentAt", last != null ? last.getSentAt().toString() : Instant.now().toString()
            );
        }).toList();

        return ResponseEntity.ok(Map.of("items", items));
    }

    @GetMapping("/conversations/{userId}/messages")
    public ResponseEntity<?> getMessages(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        int safeLimit = Math.min(Math.max(limit, 1), 300);

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Không tìm thấy người dùng"));
        }

        List<ChatMessage> rows = chatMessageRepository.findByUserIdOrderBySentAtDesc(userId, PageRequest.of(0, safeLimit));
        List<Map<String, Object>> messages = rows.reversed().stream()
                .map(m -> Map.<String, Object>of(
                        "id", m.getId(),
                        "role", m.getRole(),
                        "content", m.getContent(),
                        "sentAt", m.getSentAt().toString()
                ))
                .toList();

        return ResponseEntity.ok(Map.of(
                "user", Map.of(
                        "id", user.getId(),
                        "name", user.getName() != null ? user.getName() : "Khách hàng",
                        "email", user.getEmail() != null ? user.getEmail() : ""
                ),
                "messages", messages
        ));
    }

    @PostMapping("/conversations/{userId}/reply")
    public ResponseEntity<?> reply(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long userId,
            @RequestBody Map<String, String> body
    ) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!userRepository.existsById(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Không tìm thấy người dùng"));
        }

        String text = body != null && body.get("message") != null ? body.get("message").trim() : "";
        if (text.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Nội dung tin nhắn không được để trống"));
        }

        ChatMessage saved = chatMessageRepository.save(ChatMessage.builder()
                .userId(userId)
                .role("assistant")
                .content(text)
                .sentAt(Instant.now())
                .build());

        return ResponseEntity.ok(Map.of(
                "id", saved.getId(),
                "role", saved.getRole(),
                "content", saved.getContent(),
                "sentAt", saved.getSentAt().toString()
        ));
    }
}
