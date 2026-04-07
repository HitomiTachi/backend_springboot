package com.example.webdienthoai.service;

import com.example.webdienthoai.config.AppMailProperties;
import com.example.webdienthoai.config.AppUrlProperties;
import com.example.webdienthoai.entity.Order;
import com.example.webdienthoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderMailService {

    private static final Set<String> NOTIFY_ON_ENTER = Set.of(
            "paid",
            "shipped", "shipping",
            "delivered", "completed",
            "cancelled", "rejected", "refunded");

    private final AppMailProperties appMailProperties;
    private final AppUrlProperties appUrlProperties;
    private final UserRepository userRepository;
    private final EmailService emailService;

    /** Gọi sau khi đơn đã lưu DB lần đầu (create order). */
    public void notifyOrderCreated(Order order) {
        if (!appMailProperties.isOrderNotificationsEnabled()) {
            return;
        }
        if (order == null || order.getUserId() == null) {
            return;
        }
        userRepository.findById(order.getUserId()).ifPresent(user -> {
            String email = user.getEmail();
            if (email == null || email.isBlank()) {
                return;
            }
            String statusLabel = statusDisplayVi(order.getStatus());
            emailService.sendOrderPlacedCustomer(
                    email,
                    order.getId(),
                    order.getTotalPrice(),
                    statusLabel,
                    orderDetailLink(order.getId()));
        });
        notifyAdminsNewOrder(order);
    }

    /**
     * Gọi khi trạng thái đơn thay đổi (sau {@code OrderStatusService#changeStatus}).
     */
    public void notifyCustomerStatusChanged(Order order, String oldStatusNorm, String newStatusNorm) {
        if (!appMailProperties.isOrderNotificationsEnabled()) {
            return;
        }
        if (order == null || order.getUserId() == null) {
            return;
        }
        if (oldStatusNorm.equals(newStatusNorm)) {
            return;
        }
        if (!NOTIFY_ON_ENTER.contains(newStatusNorm)) {
            return;
        }
        userRepository.findById(order.getUserId()).ifPresent(user -> {
            String email = user.getEmail();
            if (email == null || email.isBlank()) {
                return;
            }
            String title = switch (newStatusNorm) {
                case "paid" -> "Thanh toán đã được xác nhận";
                case "shipped", "shipping" -> "Đơn hàng đang được giao";
                case "delivered", "completed" -> "Đơn hàng đã giao / hoàn tất";
                case "cancelled", "rejected" -> "Đơn hàng đã bị hủy hoặc từ chối";
                case "refunded" -> "Đơn hàng đã hoàn tiền";
                default -> "Cập nhật trạng thái đơn hàng";
            };
            String body = "<p>Trạng thái mới: <strong>" + escape(statusDisplayVi(newStatusNorm)) + "</strong></p>";
            emailService.sendOrderStatusCustomer(email, order.getId(), title, body, orderDetailLink(order.getId()));
        });
    }

    private void notifyAdminsNewOrder(Order order) {
        var admins = EmailService.parseAdminEmails(appMailProperties.getAdminNotifyEmails());
        if (admins.isEmpty() || order == null) {
            return;
        }
        userRepository.findById(order.getUserId()).ifPresent(user ->
                emailService.sendAdminNewOrder(admins, order.getId(), user.getEmail(), order.getTotalPrice()));
    }

    private String orderDetailLink(Long orderId) {
        String base = appUrlProperties.getFrontendBase().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/#/order/" + orderId;
    }

    private static String statusDisplayVi(String status) {
        if (status == null) {
            return "—";
        }
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "pending" -> "Chờ xác nhận";
            case "pending_payment" -> "Chờ thanh toán";
            case "paid" -> "Đã thanh toán";
            case "confirmed" -> "Đã xác nhận";
            case "processing" -> "Đang xử lý";
            case "shipped", "shipping" -> "Đang giao hàng";
            case "delivered" -> "Đã giao hàng";
            case "completed" -> "Hoàn tất";
            case "cancelled" -> "Đã hủy";
            case "rejected" -> "Từ chối";
            case "returned" -> "Trả hàng";
            case "refunded" -> "Đã hoàn tiền";
            default -> status;
        };
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
