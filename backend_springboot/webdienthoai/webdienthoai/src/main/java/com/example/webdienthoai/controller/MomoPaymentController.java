package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.VnpayCreatePaymentRequest;
import com.example.webdienthoai.dto.VnpayCreatePaymentResponse;
import com.example.webdienthoai.security.UserPrincipal;
import com.example.webdienthoai.service.MomoPaymentService;
import com.fasterxml.jackson.databind.JsonNode;
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
@RequestMapping("/api/payments/momo")
@RequiredArgsConstructor
public class MomoPaymentController {

    private final MomoPaymentService momoPaymentService;

    @PostMapping("/create")
    public ResponseEntity<?> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody VnpayCreatePaymentRequest body) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Vui lòng đăng nhập"));
        }
        if (!momoPaymentService.isReady()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "MoMo chưa được cấu hình trên server."));
        }
        try {
            String payUrl = momoPaymentService.createPayUrl(body.getOrderId(), principal.getUserId());
            return ResponseEntity.ok(new VnpayCreatePaymentResponse(payUrl));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Không tạo được thanh toán MoMo."));
        }
    }

    /**
     * MoMo redirect (GET) — không dùng JWT; công khai theo path.
     */
    @GetMapping("/return")
    public void returnFromMomo(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) {
                params.put(k, v[0]);
            }
        });
        String redirect = momoPaymentService.handleReturn(params);
        response.sendRedirect(redirect);
    }

    /**
     * MoMo IPN (POST JSON) — phải trả HTTP 204 theo tài liệu MoMo.
     */
    @PostMapping("/ipn")
    public ResponseEntity<Void> ipn(@RequestBody JsonNode body) {
        momoPaymentService.handleIpn(body);
        return ResponseEntity.noContent().build();
    }
}
