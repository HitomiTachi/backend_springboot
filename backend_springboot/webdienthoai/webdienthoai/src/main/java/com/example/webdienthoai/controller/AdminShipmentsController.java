package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.ShipmentDto;
import com.example.webdienthoai.dto.UpsertShipmentRequest;
import com.example.webdienthoai.entity.Order;
import com.example.webdienthoai.entity.Shipment;
import com.example.webdienthoai.repository.OrderRepository;
import com.example.webdienthoai.repository.ShipmentRepository;
import com.example.webdienthoai.service.OrderStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/orders/{orderId}/shipment")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminShipmentsController {

    private static final Set<String> ORDER_STATUSES_ALLOW_SHIPMENT = Set.of(
            "paid", "confirmed", "processing", "shipping", "shipped", "delivered");

    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final OrderStatusService orderStatusService;

    private String normalizeStatus(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "pending", "shipping", "delivered", "failed" -> s;
            default -> "";
        };
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getShipment(@PathVariable Long orderId) {
        if (!orderRepository.existsById(orderId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Shipment shipment = shipmentRepository.findByOrderId(orderId).orElse(null);
        if (shipment == null) {
            return ResponseEntity.ok(Map.of("shipment", (Object) null));
        }
        return ResponseEntity.ok(ShipmentDto.fromEntity(shipment));
    }

    @PutMapping
    @Transactional
    public ResponseEntity<?> upsertShipment(
            @PathVariable Long orderId,
            @RequestBody UpsertShipmentRequest req) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        String os = orderStatusService.normalize(order.getStatus());
        if (!ORDER_STATUSES_ALLOW_SHIPMENT.contains(os)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message",
                    "Chỉ quản lý vận chuyển khi đơn đã thanh toán/xác nhận và không ở trạng thái kết thúc (hiện tại: " + os + ")"));
        }
        Shipment shipment = shipmentRepository.findByOrderId(orderId)
                .orElseGet(() -> Shipment.builder().orderId(orderId).status("pending").build());

        if (req.getCarrier() != null) shipment.setCarrier(req.getCarrier().trim());
        if (req.getTrackingNumber() != null) shipment.setTrackingNumber(req.getTrackingNumber().trim());
        if (req.getNote() != null) shipment.setNote(req.getNote().trim());
        if (req.getStatus() != null) {
            String status = normalizeStatus(req.getStatus());
            if (status.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Shipment status không hợp lệ"));
            }
            shipment.setStatus(status);
            if ("shipping".equals(status) && shipment.getShippedAt() == null) {
                shipment.setShippedAt(Instant.now());
            }
            if ("delivered".equals(status) && shipment.getDeliveredAt() == null) {
                shipment.setDeliveredAt(Instant.now());
            }
        }

        shipment = shipmentRepository.save(shipment);
        return ResponseEntity.ok(ShipmentDto.fromEntity(shipment));
    }
}

