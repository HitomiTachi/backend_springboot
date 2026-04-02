package com.example.webdienthoai.service;

import com.example.webdienthoai.entity.Order;
import com.example.webdienthoai.entity.OrderStatusAudit;
import com.example.webdienthoai.repository.OrderStatusAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderStatusService {

    private static final Set<String> ALLOWED = Set.of(
            "pending", "pending_payment", "paid", "shipping", "completed", "cancelled", "returned", "refunded");
    private final OrderStatusAuditRepository orderStatusAuditRepository;

    public void validateStatus(String status) {
        String normalized = normalize(status);
        if (!ALLOWED.contains(normalized)) {
            throw new IllegalArgumentException("Trạng thái đơn hàng không hợp lệ");
        }
    }

    public void changeStatus(Order order, String newStatus, String actor, String note) {
        String normalized = normalize(newStatus);
        validateStatus(normalized);
        String oldStatus = normalize(order.getStatus());
        if (oldStatus.equals(normalized)) {
            return;
        }
        order.setStatus(normalized);
        orderStatusAuditRepository.save(OrderStatusAudit.builder()
                .orderId(order.getId())
                .oldStatus(oldStatus)
                .newStatus(normalized)
                .actor(actor)
                .note(note)
                .build());
    }

    public String normalize(String status) {
        return status == null ? "pending" : status.trim().toLowerCase(Locale.ROOT);
    }
}
