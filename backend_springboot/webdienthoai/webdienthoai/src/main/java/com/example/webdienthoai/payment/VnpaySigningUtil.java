package com.example.webdienthoai.payment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC SHA512 và verify chữ ký theo luồng VNPay (tham khảo tài liệu & bài mẫu, không phụ thuộc code mẫu).
 */
public final class VnpaySigningUtil {

    private VnpaySigningUtil() {
    }

    public static String hmacSHA512(String key, String data) {
        if (key == null || data == null) {
            throw new IllegalArgumentException("key/data");
        }
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKey);
            byte[] result = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Xác thực callback VNPay: tất cả tham số vnp_* (trừ vnp_SecureHash, vnp_SecureHashType), sort theo key, ký HMAC SHA512.
     */
    public static boolean verifyReturnSignature(Map<String, String> params, String hashSecret) {
        if (hashSecret == null || hashSecret.isBlank()) {
            return false;
        }
        String received = params.get("vnp_SecureHash");
        if (received == null || received.isEmpty()) {
            return false;
        }
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            String k = e.getKey();
            if (k == null || !k.startsWith("vnp_")) {
                continue;
            }
            if ("vnp_SecureHash".equals(k) || "vnp_SecureHashType".equals(k)) {
                continue;
            }
            String v = e.getValue();
            if (v != null && !v.isEmpty()) {
                sorted.put(k, v);
            }
        }
        StringBuilder sb = new StringBuilder();
        try {
            for (Map.Entry<String, String> e : sorted.entrySet()) {
                if (sb.length() > 0) {
                    sb.append('&');
                }
                sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII.toString()))
                  .append('=')
                  .append(URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII.toString()));
            }
        } catch (Exception ex) {
            return false;
        }
        String calculated = hmacSHA512(hashSecret, sb.toString());
        return received.equalsIgnoreCase(calculated);
    }
}
