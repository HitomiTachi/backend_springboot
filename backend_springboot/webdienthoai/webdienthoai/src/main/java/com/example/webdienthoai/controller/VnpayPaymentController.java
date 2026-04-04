package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.VnpayCreatePaymentRequest;
import com.example.webdienthoai.dto.VnpayCreatePaymentResponse;
import com.example.webdienthoai.security.UserPrincipal;
import com.example.webdienthoai.service.VnpayPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments/vnpay")
@RequiredArgsConstructor
public class VnpayPaymentController {

    private final VnpayPaymentService vnpayPaymentService;

    @PostMapping("/create")
    public ResponseEntity<?> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody VnpayCreatePaymentRequest body,
            HttpServletRequest request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Vui lòng đăng nhập"));
        }
        if (!vnpayPaymentService.isReady()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "VNPay chưa được cấu hình trên server."));
        }
        try {
            String ip = VnpayPaymentService.clientIp(request);
            String url = vnpayPaymentService.createPaymentUrl(body.getOrderId(), principal.getUserId(), ip);
            return ResponseEntity.ok(new VnpayCreatePaymentResponse(url));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Không tạo được thanh toán VNPay."));
        }
    }

    /**
     * VNPay redirect (GET) — không dùng JWT; công khai theo path.
     */
    @GetMapping("/return")
    public void returnFromVnpay(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) {
                params.put(k, v[0]);
            }
        });
        String redirect = vnpayPaymentService.handleReturn(params);
        response.sendRedirect(redirect);
    }

    /**
     * VNPay IPN (GET) - webhook do VNPay Server gọi vào qua background.
     */
    @GetMapping("/ipn")
    public ResponseEntity<String> ipnFromVnpay(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) {
                params.put(k, v[0]);
            }
        });
        String jsonResponse = vnpayPaymentService.handleIpn(params);
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(jsonResponse);
    }
}
