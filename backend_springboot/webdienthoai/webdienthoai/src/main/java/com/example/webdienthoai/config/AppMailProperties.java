package com.example.webdienthoai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình gửi mail. {@code enabled=false} để test/CI không gọi SMTP.
 */
@Data
@ConfigurationProperties(prefix = "app.mail")
public class AppMailProperties {

    /** Bật gửi mail thật qua JavaMailSender */
    private boolean enabled = true;

    /**
     * Địa chỉ From (RFC5322). Để trống thì dùng {@code spring.mail.username}.
     */
    private String from = "";

    /** Email chào mừng sau đăng ký */
    private boolean welcomeEnabled = true;

    /** Gửi link xác minh email sau đăng ký */
    private boolean verificationEnabled = true;

    /** Thông báo đơn hàng (khách + admin đơn mới) */
    private boolean orderNotificationsEnabled = true;

    /**
     * Danh sách email admin nhận thông báo đơn mới (phân tách dấu phẩy).
     * Ví dụ: {@code admin1@x.com,admin2@x.com}
     */
    private String adminNotifyEmails = "";
}
