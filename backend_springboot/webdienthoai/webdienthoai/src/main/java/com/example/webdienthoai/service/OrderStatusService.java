package com.example.webdienthoai.service;

import com.example.webdienthoai.entity.Order;
import com.example.webdienthoai.entity.OrderStatusAudit;
import com.example.webdienthoai.repository.OrderStatusAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderStatusService {

    /**
     * {@code shipping} giữ để tương thích dữ liệu cũ; luồng mới ưu tiên {@code shipped} (đã giao cho đơn vị vận chuyển).
     */
    private static final Set<String> ALLOWED = Set.of(
            "pending",
            "pending_payment",
            "paid",
            "confirmed",
            "processing",
            "shipped",
            "shipping",
            "delivered",
            "completed",
            "cancelled",
            "rejected",
            "returned",
            "refunded");

    private static final Set<String> TERMINAL = Set.of(
            "cancelled", "rejected", "refunded", "returned", "completed");

    private static final Map<String, Set<String>> ADMIN_TRANSITIONS = Map.ofEntries(
            Map.entry("pending", Set.of("confirmed", "rejected", "cancelled")),
            Map.entry("pending_payment", Set.of("cancelled", "rejected")),
            Map.entry("paid", Set.of("confirmed", "rejected", "cancelled")),
            Map.entry("confirmed", Set.of("processing", "shipped", "cancelled", "rejected")),
            Map.entry("processing", Set.of("shipped", "cancelled", "rejected")),
            Map.entry("shipping", Set.of("shipped", "delivered", "completed", "cancelled", "rejected")),
            Map.entry("shipped", Set.of("delivered", "completed", "cancelled", "rejected")),
            Map.entry("delivered", Set.of("completed", "cancelled", "rejected")));

    private final OrderStatusAuditRepository orderStatusAuditRepository;

    public void validateStatus(String status) {
        String normalized = normalize(status);
        if (!ALLOWED.contains(normalized)) {
            throw new IllegalArgumentException("Trạng thái đơn hàng không hợp lệ");
        }
    }

    /**
     * Kiểm tra chuyển trạng thái do admin. Luồng VNPay / return gọi {@link #changeStatus} trực tiếp, không qua đây.
     */
    public void validateAdminTransition(String fromStatus, String toStatus) {
        String from = normalize(fromStatus);
        String to = normalize(toStatus);
        if (from.equals(to)) {
            return;
        }
        validateStatus(to);
        if (TERMINAL.contains(from)) {
            throw new IllegalArgumentException("Không thể đổi trạng thái đơn đã kết thúc");
        }
        Set<String> allowed = ADMIN_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new IllegalArgumentException("Không được chuyển từ \"" + from + "\" sang \"" + to + "\"");
        }
    }

    public boolean canCustomerCancel(String status) {
        String n = normalize(status);
        return "pending".equals(n) || "pending_payment".equals(n);
    }

    /** Đơn không còn chờ thanh toán VNPay — IPN không ghi paid lại. */
    public boolean isVnpayAlreadyFinalized(String status) {
        String n = normalize(status);
        return !"pending_payment".equals(n) && !"pending".equals(n);
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
