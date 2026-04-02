package com.example.webdienthoai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình MoMo Payment Gateway (sandbox/prod). Bật khi {@link #enabled} và đủ partner/access/secret.
 */
@Data
@ConfigurationProperties(prefix = "app.momo")
public class MomoProperties {

    private boolean enabled = false;

    /** POST /v2/gateway/api/create */
    private String createUrl = "https://test-payment.momo.vn/v2/gateway/api/create";

    private String partnerCode = "";

    private String accessKey = "";

    private String secretKey = "";

    /** captureWallet (theo tài liệu MoMo v3) */
    private String requestType = "captureWallet";
}
