package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.AdminInboxItemDto;
import com.example.webdienthoai.entity.AdminInboxRead;
import com.example.webdienthoai.entity.Order;
import com.example.webdienthoai.entity.OrderStatusAudit;
import com.example.webdienthoai.repository.AdminInboxReadRepository;
import com.example.webdienthoai.repository.OrderRepository;
import com.example.webdienthoai.repository.OrderStatusAuditRepository;
import com.example.webdienthoai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/inbox")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminInboxController {
    private final OrderStatusAuditRepository orderStatusAuditRepository;
    private final OrderRepository orderRepository;
    private final AdminInboxReadRepository adminInboxReadRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getInbox(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "30") int limit
    ) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        int size = Math.min(Math.max(limit, 1), 100);
        List<OrderStatusAudit> audits = orderStatusAuditRepository
                .findAllByOrderByChangedAtDesc(PageRequest.of(0, size))
                .getContent();

        List<Long> orderIds = audits.stream().map(OrderStatusAudit::getOrderId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Order> orderMap = orderRepository.findAllById(orderIds)
                .stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));

        List<Long> auditIds = audits.stream().map(OrderStatusAudit::getId).toList();
        Set<Long> readAuditIds = adminInboxReadRepository
                .findByAdminUserIdAndAuditIdIn(principal.getUserId(), auditIds)
                .stream()
                .map(AdminInboxRead::getAuditId)
                .collect(Collectors.toSet());

        List<AdminInboxItemDto> items = audits.stream().map(audit -> {
            Order order = orderMap.get(audit.getOrderId());
            String customerName = order != null && order.getUser() != null && order.getUser().getName() != null
                    ? order.getUser().getName()
                    : "Khách hàng";
            return AdminInboxItemDto.builder()
                    .auditId(audit.getId())
                    .orderId(audit.getOrderId())
                    .customerName(customerName)
                    .oldStatus(audit.getOldStatus())
                    .newStatus(audit.getNewStatus())
                    .actor(audit.getActor())
                    .note(audit.getNote())
                    .changedAt(audit.getChangedAt())
                    .read(readAuditIds.contains(audit.getId()))
                    .build();
        }).toList();

        long unreadCount = items.stream().filter(i -> !i.isRead()).count();
        return ResponseEntity.ok(Map.of("items", items, "unreadCount", unreadCount));
    }

    @PatchMapping("/{auditId}/read")
    @Transactional
    public ResponseEntity<?> markRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long auditId
    ) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!orderStatusAuditRepository.existsById(auditId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Không tìm thấy thông báo"));
        }
        adminInboxReadRepository.findByAdminUserIdAndAuditId(principal.getUserId(), auditId)
                .orElseGet(() -> adminInboxReadRepository.save(AdminInboxRead.builder()
                        .adminUserId(principal.getUserId())
                        .auditId(auditId)
                        .readAt(Instant.now())
                        .build()));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PatchMapping("/read-all")
    @Transactional
    public ResponseEntity<?> markAllRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "100") int limit
    ) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        int size = Math.min(Math.max(limit, 1), 500);
        List<OrderStatusAudit> audits = orderStatusAuditRepository
                .findAllByOrderByChangedAtDesc(PageRequest.of(0, size))
                .getContent();
        List<Long> auditIds = audits.stream().map(OrderStatusAudit::getId).toList();
        Set<Long> existing = adminInboxReadRepository
                .findByAdminUserIdAndAuditIdIn(principal.getUserId(), auditIds)
                .stream()
                .map(AdminInboxRead::getAuditId)
                .collect(Collectors.toSet());

        List<AdminInboxRead> toCreate = auditIds.stream()
                .filter(id -> !existing.contains(id))
                .map(id -> AdminInboxRead.builder()
                        .adminUserId(principal.getUserId())
                        .auditId(id)
                        .readAt(Instant.now())
                        .build())
                .toList();
        if (!toCreate.isEmpty()) adminInboxReadRepository.saveAll(toCreate);
        return ResponseEntity.ok(Map.of("ok", true, "updated", toCreate.size()));
    }
}
