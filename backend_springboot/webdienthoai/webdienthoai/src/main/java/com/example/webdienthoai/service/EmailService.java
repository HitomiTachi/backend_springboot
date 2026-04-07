package com.example.webdienthoai.service;

import com.example.webdienthoai.config.AppMailProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final AppMailProperties appMailProperties;

    @Value("${spring.mail.username:}")
    private String springMailUsername;

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        String html = """
                <p>Xin chào,</p>
                <p>Bạn đã yêu cầu đặt lại mật khẩu. Nhấn vào liên kết sau (có hiệu lực giới hạn):</p>
                <p><a href="%s">Đặt lại mật khẩu</a></p>
                <p>Nếu bạn không yêu cầu, hãy bỏ qua email này.</p>
                """.formatted(resetLink);
        sendHtml(toEmail, "Đặt lại mật khẩu — Web điện thoại", html, "đặt lại mật khẩu");
    }

    public void sendWelcomeEmail(String toEmail, String displayName) {
        String name = displayName != null && !displayName.isBlank() ? displayName : "bạn";
        String html = """
                <p>Xin chào %s,</p>
                <p>Cảm ơn bạn đã đăng ký tài khoản tại cửa hàng điện thoại của chúng tôi.</p>
                <p>Chúc bạn mua sắm vui vẻ!</p>
                """.formatted(escapeHtml(name));
        sendHtml(toEmail, "Chào mừng bạn — Web điện thoại", html, "chào mừng");
    }

    public void sendEmailVerification(String toEmail, String verifyLink) {
        String html = """
                <p>Xin chào,</p>
                <p>Vui lòng xác minh địa chỉ email bằng liên kết sau (có hiệu lực giới hạn):</p>
                <p><a href="%s">Xác minh email</a></p>
                <p>Nếu bạn không đăng ký tài khoản, hãy bỏ qua email này.</p>
                """.formatted(verifyLink);
        sendHtml(toEmail, "Xác minh email — Web điện thoại", html, "xác minh email");
    }

    public void sendOrderPlacedCustomer(String toEmail, Long orderId, BigDecimal total, String statusLabel, String orderLink) {
        String html = """
                <p>Đơn hàng <strong>#%d</strong> của bạn đã được ghi nhận.</p>
                <p>Trạng thái: <strong>%s</strong></p>
                <p>Tổng thanh toán: <strong>%s</strong> VNĐ</p>
                <p><a href="%s">Xem chi tiết đơn hàng</a></p>
                """.formatted(orderId, escapeHtml(statusLabel), formatMoney(total), orderLink);
        sendHtml(toEmail, "Đã đặt hàng #" + orderId, html, "đơn đặt hàng");
    }

    public void sendOrderStatusCustomer(String toEmail, Long orderId, String title, String bodyHtml, String orderLink) {
        String html = "<p>" + escapeHtml(title) + "</p>" + bodyHtml
                + "<p><a href=\"" + orderLink + "\">Xem đơn hàng</a></p>";
        sendHtml(toEmail, "Cập nhật đơn #" + orderId, html, "cập nhật đơn");
    }

    public void sendAdminNewOrder(List<String> adminEmails, Long orderId, String customerEmail, BigDecimal total) {
        if (adminEmails == null || adminEmails.isEmpty()) {
            return;
        }
        String html = """
                <p>Có đơn hàng mới <strong>#%d</strong>.</p>
                <p>Khách: %s</p>
                <p>Tổng: <strong>%s</strong> VNĐ</p>
                """.formatted(orderId, escapeHtml(customerEmail), formatMoney(total));
        for (String addr : adminEmails) {
            if (addr != null && !addr.isBlank()) {
                sendHtml(addr.trim(), "[Admin] Đơn mới #" + orderId, html, "thông báo admin");
            }
        }
    }

    private void sendHtml(String toEmail, String subject, String html, String logLabel) {
        if (!appMailProperties.isEnabled()) {
            log.debug("app.mail.enabled=false — bỏ qua gửi mail ({}) tới {}", logLabel, toEmail);
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("JavaMailSender không khả dụng — không gửi mail ({})", logLabel);
            return;
        }
        String from = resolveFrom();
        if (from == null || from.isBlank()) {
            log.warn("Thiếu app.mail.from và spring.mail.username — không gửi mail ({})", logLabel);
            return;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
            log.info("Đã gửi mail ({}) tới {}", logLabel, toEmail);
        } catch (Exception e) {
            log.error("Gửi mail ({}) thất bại tới {}: {}", logLabel, toEmail, e.getMessage());
        }
    }

    private String resolveFrom() {
        String configured = appMailProperties.getFrom();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return springMailUsername != null ? springMailUsername.trim() : "";
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String formatMoney(BigDecimal v) {
        if (v == null) {
            return "0";
        }
        return v.stripTrailingZeros().toPlainString();
    }

    /** Parse {@link AppMailProperties#getAdminNotifyEmails()} thành danh sách. */
    public static List<String> parseAdminEmails(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
