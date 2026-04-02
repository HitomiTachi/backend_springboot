package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.CreateReturnRequest;
import com.example.webdienthoai.dto.ReturnRequestDto;
import com.example.webdienthoai.dto.UpdateReturnStatusRequest;
import com.example.webdienthoai.entity.Order;
import com.example.webdienthoai.entity.OrderItem;
import com.example.webdienthoai.entity.Product;
import com.example.webdienthoai.entity.ReturnRequest;
import com.example.webdienthoai.repository.OrderRepository;
import com.example.webdienthoai.repository.ProductRepository;
import com.example.webdienthoai.repository.ReturnRequestRepository;
import com.example.webdienthoai.service.OrderStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminReturnsController {
    private final ReturnRequestRepository returnRequestRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderStatusService orderStatusService;

    private String normalizeStatus(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    @GetMapping("/returns")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ReturnRequestDto>> listReturns(@RequestParam(required = false) String status) {
        List<ReturnRequest> data = (status == null || status.isBlank())
                ? returnRequestRepository.findAll()
                : returnRequestRepository.findByStatusOrderByCreatedAtDesc(normalizeStatus(status));
        return ResponseEntity.ok(data.stream().map(ReturnRequestDto::fromEntity).toList());
    }

    @GetMapping("/orders/{orderId}/returns")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getOrderReturns(@PathVariable Long orderId) {
        if (!orderRepository.existsById(orderId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        List<ReturnRequestDto> items = returnRequestRepository.findByOrderIdOrderByCreatedAtDesc(orderId)
                .stream().map(ReturnRequestDto::fromEntity).toList();
        return ResponseEntity.ok(Map.of("items", items));
    }

    @PostMapping("/orders/{orderId}/returns")
    @Transactional
    public ResponseEntity<?> createReturn(@PathVariable Long orderId, @RequestBody CreateReturnRequest req) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        ReturnRequest rr = ReturnRequest.builder()
                .orderId(orderId)
                .status("requested")
                .reason(req.getReason())
                .refundAmount(req.getRefundAmount())
                .note(req.getNote())
                .restocked(false)
                .build();
        rr = returnRequestRepository.save(rr);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReturnRequestDto.fromEntity(rr));
    }

    @PatchMapping("/returns/{returnId}/status")
    @Transactional
    public ResponseEntity<?> updateReturnStatus(
            @PathVariable Long returnId,
            @RequestBody UpdateReturnStatusRequest req) {
        ReturnRequest rr = returnRequestRepository.findById(returnId).orElse(null);
        if (rr == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        String newStatus = normalizeStatus(req.getStatus());
        if (!List.of("approved", "rejected", "refunded").contains(newStatus)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Return status không hợp lệ"));
        }
        Order order = orderRepository.findById(rr.getOrderId()).orElse(null);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Order không tồn tại"));
        }

        if ("approved".equals(newStatus) && !Boolean.TRUE.equals(rr.getRestocked())) {
            for (OrderItem item : order.getItems()) {
                Product p = item.getProduct();
                if (p != null) {
                    int stock = p.getStock() != null ? p.getStock() : 0;
                    p.setStock(stock + (item.getQuantity() != null ? item.getQuantity() : 0));
                    productRepository.save(p);
                }
            }
            rr.setRestocked(true);
            orderStatusService.changeStatus(order, "returned", "admin", "Admin duyệt return và hoàn kho");
            orderRepository.save(order);
        }

        if ("refunded".equals(newStatus)) {
            orderStatusService.changeStatus(order, "refunded", "admin", "Admin hoàn tiền đơn hàng");
            orderRepository.save(order);
        }

        rr.setStatus(newStatus);
        if (req.getNote() != null) {
            rr.setNote(req.getNote());
        }
        rr = returnRequestRepository.save(rr);
        return ResponseEntity.ok(ReturnRequestDto.fromEntity(rr));
    }
}

