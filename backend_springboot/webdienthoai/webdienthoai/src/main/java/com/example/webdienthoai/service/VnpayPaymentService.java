package com.example.webdienthoai.service;

import com.example.webdienthoai.config.AppUrlProperties;
import com.example.webdienthoai.config.VnpayProperties;
import com.example.webdienthoai.entity.Order;
import com.example.webdienthoai.payment.VnpaySigningUtil;
import com.example.webdienthoai.repository.OrderRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class VnpayPaymentService {

    private static final Pattern TXN_REF_ORDER = Pattern.compile("^WD(\\d+)T\\d+$");

    private final VnpayProperties vnpayProperties;
    private final AppUrlProperties appUrlProperties;
    private final OrderRepository orderRepository;
    private final OrderStatusService orderStatusService;

    public boolean isReady() {
        return vnpayProperties.isEnabled()
                && vnpayProperties.getTmnCode() != null && !vnpayProperties.getTmnCode().isBlank()
                && vnpayProperties.getHashSecret() != null && !vnpayProperties.getHashSecret().isBlank();
    }

    /**
     * Tạo URL chuyển hướng VNPay. Chỉ cho đơn {@code pending_payment}, phương thức {@code vnpay}, đúng chủ đơn.
     */
    @Transactional(readOnly = true)
    public String createPaymentUrl(Long orderId, Long userId, String clientIp) {
        if (!isReady()) {
            throw new IllegalStateException("VNPay chưa được bật hoặc thiếu TMN / Hash Secret.");
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        if (order.getUser() == null || !order.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Không có quyền thanh toán đơn này.");
        }
        if (!"vnpay".equalsIgnoreCase(String.valueOf(order.getPaymentMethod()))) {
            throw new IllegalArgumentException("Đơn không dùng phương thức VNPay.");
        }
        if (!"pending_payment".equals(orderStatusService.normalize(order.getStatus()))) {
            throw new IllegalArgumentException("Đơn không ở trạng thái chờ thanh toán.");
        }

        long amountVnp = order.getTotalPrice()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        String vnp_Version = "2.1.0";
        String vnp_Command = "pay";
        String orderType = "other";
        String vnp_TxnRef = "WD" + orderId + "T" + System.currentTimeMillis();
        String vnp_IpAddr = (clientIp != null && !clientIp.isBlank()) ? clientIp : "127.0.0.1";
        String vnp_TmnCode = vnpayProperties.getTmnCode();

        String returnUrl = trimTrailingSlash(appUrlProperties.getApiPublicBase())
                + "/api/payments/vnpay/return";

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnp_Version);
        vnp_Params.put("vnp_Command", vnp_Command);
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amountVnp));
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", "Thanh toan don hang #" + orderId);
        vnp_Params.put("vnp_OrderType", orderType);
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", returnUrl);
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        vnp_Params.put("vnp_CreateDate", formatter.format(cld.getTime()));
        cld.add(Calendar.MINUTE, 15);
        vnp_Params.put("vnp_ExpireDate", formatter.format(cld.getTime()));

        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        for (String fieldName : fieldNames) {
            String fieldValue = vnp_Params.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                hashData.append(fieldName).append('=')
                        .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII))
                        .append('=')
                        .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                query.append('&');
                hashData.append('&');
            }
        }
        if (query.length() > 0) {
            query.setLength(query.length() - 1);
        }
        if (hashData.length() > 0) {
            hashData.setLength(hashData.length() - 1);
        }
        String vnp_SecureHash = VnpaySigningUtil.hmacSHA512(vnpayProperties.getHashSecret(), hashData.toString());
        query.append("&vnp_SecureHash=").append(vnp_SecureHash);
        return vnpayProperties.getPayUrl() + "?" + query;
    }

    /**
     * Xử lý return URL VNPay: verify chữ ký, khớp số tiền, cập nhật đơn {@code paid} khi thành công.
     *
     * @return URL frontend để redirect (HTTP 302).
     */
    @Transactional
    public String handleReturn(Map<String, String> params) {
        String base = trimTrailingSlash(appUrlProperties.getFrontendBase());
        if (!isReady()) {
            return base + "/checkout?payment=failed&reason=" + urlEncode("VNPay chưa cấu hình");
        }
        if (!VnpaySigningUtil.verifyReturnSignature(params, vnpayProperties.getHashSecret())) {
            return base + "/checkout?payment=failed&reason=" + urlEncode("Chữ ký không hợp lệ");
        }
        String txnRef = params.get("vnp_TxnRef");
        Long orderId = parseOrderIdFromTxnRef(txnRef);
        if (orderId == null) {
            return base + "/checkout?payment=failed&reason=" + urlEncode("Mã giao dịch không hợp lệ");
        }
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return base + "/checkout?payment=failed&reason=" + urlEncode("Không tìm thấy đơn");
        }
        String rsp = params.get("vnp_ResponseCode");
        String amountStr = params.get("vnp_Amount");
        long expectedVnp = order.getTotalPrice()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        long returnedVnp = -1;
        try {
            returnedVnp = Long.parseLong(amountStr);
        } catch (Exception ignored) {
            // fall through
        }
        if (returnedVnp != expectedVnp) {
            return base + "/order-confirmation/" + orderId + "?payment=failed&reason=" + urlEncode("Số tiền không khớp");
        }
        if ("00".equals(rsp) && "vnpay".equalsIgnoreCase(String.valueOf(order.getPaymentMethod()))) {
            if ("pending_payment".equals(orderStatusService.normalize(order.getStatus()))) {
                orderStatusService.changeStatus(order, "paid", "vnpay", "Thanh toán VNPay thành công (return URL)");
                orderRepository.save(order);
            }
            return base + "/order-confirmation/" + orderId + "?payment=success";
        }
        return base + "/order-confirmation/" + orderId + "?payment=failed&reason=" + urlEncode(
                rsp != null ? ("Mã lỗi VNPay: " + rsp) : "Thanh toán không thành công");
    }

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }

    private static Long parseOrderIdFromTxnRef(String txnRef) {
        if (txnRef == null) {
            return null;
        }
        Matcher m = TXN_REF_ORDER.matcher(txnRef.trim());
        if (!m.matches()) {
            return null;
        }
        try {
            return Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trimTrailingSlash(String u) {
        if (u == null || u.isEmpty()) {
            return "";
        }
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Xử lý IPN từ VNPay (Server-to-Server). Trả về JSON chuẩn VNPay.
     */
    @Transactional
    public String handleIpn(Map<String, String> params) {
        if (!isReady()) {
            return "{\"RspCode\":\"99\",\"Message\":\"Unknown error\"}";
        }
        if (!VnpaySigningUtil.verifyReturnSignature(params, vnpayProperties.getHashSecret())) {
            return "{\"RspCode\":\"97\",\"Message\":\"Invalid signature\"}";
        }
        String txnRef = params.get("vnp_TxnRef");
        Long orderId = parseOrderIdFromTxnRef(txnRef);
        if (orderId == null) {
            return "{\"RspCode\":\"01\",\"Message\":\"Order not found\"}";
        }
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return "{\"RspCode\":\"01\",\"Message\":\"Order not found\"}";
        }

        long expectedVnp = order.getTotalPrice()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        long returnedVnp = -1;
        try {
            String amtStr = params.get("vnp_Amount");
            if (amtStr != null) {
                returnedVnp = Long.parseLong(amtStr);
            }
        } catch (Exception ignored) { }

        if (expectedVnp != returnedVnp) {
            return "{\"RspCode\":\"04\",\"Message\":\"Invalid amount\"}";
        }

        String rsp = params.get("vnp_ResponseCode");
        String currentStatus = orderStatusService.normalize(order.getStatus());

        if ("paid".equals(currentStatus) || "completed".equals(currentStatus) || "cancelled".equals(currentStatus) || "shipping".equals(currentStatus)) {
            return "{\"RspCode\":\"02\",\"Message\":\"Order already confirmed\"}";
        }

        if ("00".equals(rsp) && "vnpay".equalsIgnoreCase(String.valueOf(order.getPaymentMethod()))) {
            if ("pending_payment".equals(currentStatus) || "pending".equals(currentStatus)) {
                orderStatusService.changeStatus(order, "paid", "vnpay_ipn", "Thanh toán VNPay thành công (IPN)");
                orderRepository.save(order);
            }
        } else {
            if ("pending_payment".equals(currentStatus)) {
                orderStatusService.changeStatus(order, "cancelled", "vnpay_ipn", "Thanh toán VNPay thất bại mã: " + rsp);
                orderRepository.save(order);
            }
        }
        return "{\"RspCode\":\"00\",\"Message\":\"Confirm Success\"}";
    }
}
