package com.example.webdienthoai.service;

import com.example.webdienthoai.config.AppUrlProperties;
import com.example.webdienthoai.config.MomoProperties;
import com.example.webdienthoai.entity.Order;
import com.example.webdienthoai.payment.MomoSigningUtil;
import com.example.webdienthoai.repository.OrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MomoPaymentService {

    private static final Pattern ORDER_REF = Pattern.compile("^WD(\\d+)T\\d+$");

    private final MomoProperties momoProperties;
    private final AppUrlProperties appUrlProperties;
    private final OrderRepository orderRepository;
    private final OrderStatusService orderStatusService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public boolean isReady() {
        return momoProperties.isEnabled()
                && momoProperties.getPartnerCode() != null && !momoProperties.getPartnerCode().isBlank()
                && momoProperties.getAccessKey() != null && !momoProperties.getAccessKey().isBlank()
                && momoProperties.getSecretKey() != null && !momoProperties.getSecretKey().isBlank();
    }

    /**
     * Gọi MoMo create, trả về payUrl. Chỉ cho đơn {@code pending_payment}, {@code momo}, đúng chủ đơn.
     */
    @Transactional(readOnly = true)
    public String createPayUrl(Long orderId, Long userId) {
        if (!isReady()) {
            throw new IllegalStateException("MoMo chưa được bật hoặc thiếu cấu hình.");
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        if (order.getUser() == null || !order.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Không có quyền thanh toán đơn này.");
        }
        if (!"momo".equalsIgnoreCase(String.valueOf(order.getPaymentMethod()))) {
            throw new IllegalArgumentException("Đơn không dùng phương thức MoMo.");
        }
        if (!"pending_payment".equals(orderStatusService.normalize(order.getStatus()))) {
            throw new IllegalArgumentException("Đơn không ở trạng thái chờ thanh toán.");
        }

        long amountVnd = order.getTotalPrice()
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        if (amountVnd < 1000) {
            throw new IllegalArgumentException("Số tiền không hợp lệ cho MoMo (tối thiểu 1.000 VND).");
        }

        String requestId = momoProperties.getPartnerCode() + System.currentTimeMillis();
        String orderRef = "WD" + orderId + "T" + System.currentTimeMillis();
        String orderInfo = "Thanh toan don hang #" + orderId;
        String extraData = "";
        String redirectUrl = trimTrailingSlash(appUrlProperties.getApiPublicBase())
                + "/api/payments/momo/return";
        String ipnUrl = trimTrailingSlash(appUrlProperties.getApiPublicBase())
                + "/api/payments/momo/ipn";

        String amountStr = String.valueOf(amountVnd);
        String raw = MomoSigningUtil.rawSignatureCreate(
                momoProperties.getAccessKey(),
                amountStr,
                extraData,
                ipnUrl,
                orderRef,
                orderInfo,
                momoProperties.getPartnerCode(),
                redirectUrl,
                requestId,
                momoProperties.getRequestType());
        String signature = MomoSigningUtil.hmacSha256Hex(raw, momoProperties.getSecretKey());

        Map<String, Object> body = new HashMap<>();
        body.put("partnerCode", momoProperties.getPartnerCode());
        body.put("accessKey", momoProperties.getAccessKey());
        body.put("requestId", requestId);
        body.put("amount", amountStr);
        body.put("orderId", orderRef);
        body.put("orderInfo", orderInfo);
        body.put("redirectUrl", redirectUrl);
        body.put("ipnUrl", ipnUrl);
        body.put("extraData", extraData);
        body.put("requestType", momoProperties.getRequestType());
        body.put("signature", signature);
        body.put("lang", "vi");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được body MoMo.", e);
        }

        String responseJson;
        try {
            responseJson = restTemplate.postForObject(
                    momoProperties.getCreateUrl(),
                    new HttpEntity<>(json, headers),
                    String.class);
        } catch (Exception e) {
            throw new IllegalStateException("Không gọi được MoMo: " + e.getMessage(), e);
        }
        if (responseJson == null || responseJson.isBlank()) {
            throw new IllegalStateException("MoMo không trả dữ liệu.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(responseJson);
        } catch (Exception e) {
            throw new IllegalStateException("Phản hồi MoMo không phải JSON hợp lệ.");
        }
        int resultCode = root.path("resultCode").asInt(-1);
        if (resultCode != 0) {
            String msg = root.path("message").asText("Lỗi MoMo");
            throw new IllegalStateException(msg + " (resultCode=" + resultCode + ")");
        }
        String payUrl = root.path("payUrl").asText(null);
        if (payUrl == null || payUrl.isBlank()) {
            throw new IllegalStateException("MoMo không trả payUrl.");
        }
        return payUrl;
    }

    /**
     * Redirect sau thanh toán (GET) — verify chữ ký, cập nhật đơn khi thành công.
     */
    @Transactional
    public String handleReturn(Map<String, String> params) {
        String base = trimTrailingSlash(appUrlProperties.getFrontendBase());
        if (!isReady()) {
            return base + "/checkout?payment=failed&reason=" + urlEncode("MoMo chưa cấu hình");
        }
        if (!verifyResultSignature(params)) {
            return base + "/checkout?payment=failed&reason=" + urlEncode("Chữ ký không hợp lệ");
        }
        return processResultAndRedirect(params, base, "MoMo (return URL)");
    }

    /**
     * IPN server-to-server — verify chữ ký, cập nhật đơn; luôn xử lý idempotent.
     */
    @Transactional
    public void handleIpn(JsonNode body) {
        if (!isReady() || body == null || body.isNull()) {
            return;
        }
        Map<String, String> map = new HashMap<>();
        body.fieldNames().forEachRemaining(key -> {
            if ("signature".equals(key)) {
                return;
            }
            JsonNode val = body.path(key);
            if (val.isMissingNode() || val.isNull()) {
                return;
            }
            if (val.isNumber()) {
                map.put(key, val.numberValue().toString());
            } else {
                map.put(key, val.asText(""));
            }
        });
        if (!verifyResultSignature(map)) {
            return;
        }
        processResult(map, "MoMo (IPN)");
    }

    private String processResultAndRedirect(Map<String, String> params, String base, String note) {
        Long orderId = parseOrderIdFromRef(params.get("orderId"));
        if (orderId == null) {
            return base + "/checkout?payment=failed&reason=" + urlEncode("Mã đơn không hợp lệ");
        }
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return base + "/checkout?payment=failed&reason=" + urlEncode("Không tìm thấy đơn");
        }
        long expected = order.getTotalPrice().setScale(0, RoundingMode.HALF_UP).longValueExact();
        long returned;
        try {
            returned = Long.parseLong(params.get("amount"));
        } catch (Exception e) {
            return base + "/order-confirmation/" + orderId + "?payment=failed&reason=" + urlEncode("Số tiền không hợp lệ");
        }
        if (returned != expected) {
            return base + "/order-confirmation/" + orderId + "?payment=failed&reason=" + urlEncode("Số tiền không khớp");
        }
        int rc = parseInt(params.get("resultCode"), -1);
        if ((rc == 0 || rc == 9000)
                && "momo".equalsIgnoreCase(String.valueOf(order.getPaymentMethod()))) {
            if ("pending_payment".equals(orderStatusService.normalize(order.getStatus()))) {
                orderStatusService.changeStatus(order, "paid", "momo", "Thanh toán " + note);
                orderRepository.save(order);
            }
            return base + "/order-confirmation/" + orderId + "?payment=success";
        }
        return base + "/order-confirmation/" + orderId + "?payment=failed&reason=" + urlEncode(
                rc >= 0 ? ("Mã lỗi MoMo: " + rc) : "Thanh toán không thành công");
    }

    private void processResult(Map<String, String> params, String note) {
        Long orderId = parseOrderIdFromRef(params.get("orderId"));
        if (orderId == null) {
            return;
        }
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return;
        }
        long expected = order.getTotalPrice().setScale(0, RoundingMode.HALF_UP).longValueExact();
        long returned;
        try {
            returned = Long.parseLong(params.get("amount"));
        } catch (Exception e) {
            return;
        }
        if (returned != expected) {
            return;
        }
        int rc = parseInt(params.get("resultCode"), -1);
        if ((rc == 0 || rc == 9000)
                && "momo".equalsIgnoreCase(String.valueOf(order.getPaymentMethod()))
                && "pending_payment".equals(orderStatusService.normalize(order.getStatus()))) {
            orderStatusService.changeStatus(order, "paid", "momo", "Thanh toán " + note);
            orderRepository.save(order);
        }
    }

    private boolean verifyResultSignature(Map<String, String> p) {
        if (p == null) {
            return false;
        }
        String signature = p.get("signature");
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String raw = MomoSigningUtil.rawSignatureResult(
                momoProperties.getAccessKey(),
                p.get("amount"),
                p.get("extraData"),
                p.get("message"),
                p.get("orderId"),
                p.get("orderInfo"),
                p.get("orderType"),
                p.get("partnerCode"),
                p.get("payType"),
                p.get("requestId"),
                p.get("responseTime"),
                p.get("resultCode"),
                p.get("transId"));
        String expect = MomoSigningUtil.hmacSha256Hex(raw, momoProperties.getSecretKey());
        return signature.equalsIgnoreCase(expect);
    }

    private static Long parseOrderIdFromRef(String orderRef) {
        if (orderRef == null) {
            return null;
        }
        Matcher m = ORDER_REF.matcher(orderRef.trim());
        if (!m.matches()) {
            return null;
        }
        try {
            return Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseInt(String s, int def) {
        if (s == null || s.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
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
            return URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
