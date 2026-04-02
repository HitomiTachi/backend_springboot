package com.example.webdienthoai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình VNPay (sandbox/prod). Bật khi {@link #enabled} và đủ {@link #tmnCode}, {@link #hashSecret}.
 */
@Data
@ConfigurationProperties(prefix = "app.vnpay")
public class VnpayProperties {

    private boolean enabled = false;

    private String payUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";

    private String tmnCode = "";

    private String hashSecret = "";
}
