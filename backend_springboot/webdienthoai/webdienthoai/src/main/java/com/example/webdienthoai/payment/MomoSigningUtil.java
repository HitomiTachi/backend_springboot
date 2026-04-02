package com.example.webdienthoai.payment;

import java.nio.charset.StandardCharsets;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 (hex) theo tài liệu MoMo Payment Gateway.
 */
public final class MomoSigningUtil {

    private MomoSigningUtil() {
    }

    public static String hmacSha256Hex(String data, String secretKey) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac.init(key);
            byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MoMo HMAC-SHA256 failed", e);
        }
    }

    /**
     * Raw string ký request create (captureWallet).
     */
    public static String rawSignatureCreate(
            String accessKey,
            String amount,
            String extraData,
            String ipnUrl,
            String orderId,
            String orderInfo,
            String partnerCode,
            String redirectUrl,
            String requestId,
            String requestType) {
        return String.format(
                "accessKey=%s&amount=%s&extraData=%s&ipnUrl=%s&orderId=%s&orderInfo=%s&partnerCode=%s&redirectUrl=%s&requestId=%s&requestType=%s",
                n(accessKey), n(amount), n(extraData), n(ipnUrl), n(orderId), n(orderInfo),
                n(partnerCode), n(redirectUrl), n(requestId), n(requestType));
    }

    /**
     * Raw string ký kết quả giao dịch (redirect URL query hoặc body IPN).
     */
    public static String rawSignatureResult(
            String accessKey,
            String amount,
            String extraData,
            String message,
            String orderId,
            String orderInfo,
            String orderType,
            String partnerCode,
            String payType,
            String requestId,
            String responseTime,
            String resultCode,
            String transId) {
        return String.format(
                "accessKey=%s&amount=%s&extraData=%s&message=%s&orderId=%s&orderInfo=%s&orderType=%s&partnerCode=%s&payType=%s&requestId=%s&responseTime=%s&resultCode=%s&transId=%s",
                n(accessKey), n(amount), n(extraData), n(message), n(orderId), n(orderInfo), n(orderType),
                n(partnerCode), n(payType), n(requestId), n(responseTime), n(resultCode), n(transId));
    }

    private static String n(String s) {
        return s == null ? "" : s;
    }
}
