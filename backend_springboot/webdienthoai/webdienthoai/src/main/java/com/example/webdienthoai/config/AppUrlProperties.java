package com.example.webdienthoai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * URL công khai của API (để ghép VNPay return URL) và frontend (redirect sau thanh toán).
 * Dùng prefix {@code app.urls} để không trùng {@code app.jwt} / {@code app.vnpay}.
 */
@Data
@ConfigurationProperties(prefix = "app.urls")
public class AppUrlProperties {

    /** Ví dụ http://localhost:8080 — không có dấu / cuối */
    private String apiPublicBase = "http://localhost:8080";

    /** Ví dụ http://localhost:3000 */
    private String frontendBase = "http://localhost:3000";
}
