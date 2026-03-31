package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.*;
import com.example.webdienthoai.entity.*;
import com.example.webdienthoai.repository.OrderRepository;
import com.example.webdienthoai.repository.ProductRepository;
import com.example.webdienthoai.repository.UserRepository;
import com.example.webdienthoai.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrdersController {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @PostMapping
    public ResponseEntity<?> createOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateOrderRequest req) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Vui lòng đăng nhập"));
        }
        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Order order = Order.builder()
                .user(user)
            .shippingAddressId(req.getShippingAddressId())
            .subtotal(Objects.requireNonNullElse(req.getSubtotal(), req.getTotalPrice()))
            .discountAmount(Objects.requireNonNullElse(req.getDiscountAmount(), java.math.BigDecimal.ZERO))
            .shippingCost(Objects.requireNonNullElse(req.getShippingCost(), java.math.BigDecimal.ZERO))
                .totalPrice(req.getTotalPrice())
            .paymentMethod(req.getPaymentMethod())
            .notes(req.getNotes())
            // COD (thanh toán khi nhận hàng) => chưa thanh toán ngay
            // Card/PayPal => simulate "đã thanh toán" ngay khi đặt hàng
            .status(
                    req.getPaymentMethod() == null || req.getPaymentMethod().isBlank()
                            ? "pending"
                            : "cash_on_delivery".equalsIgnoreCase(req.getPaymentMethod())
                                ? "pending"
                                : "paid"
            )
                .items(new ArrayList<>())
                .build();

        for (OrderItemRequest itemReq : req.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + itemReq.getProductId()));
            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .productName(product.getName())
                    .productImage(product.getImage())
                    .quantity(itemReq.getQuantity())
                    .priceAtOrder(itemReq.getPrice())
                    .lineTotal(itemReq.getPrice().multiply(java.math.BigDecimal.valueOf(itemReq.getQuantity())))
                    .build();
            order.getItems().add(item);
        }

        order = orderRepository.save(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderDto.fromEntity(order));
    }

    /**
     * COD: khách xác nhận đã nhận hàng -> ghi nhận thanh toán.
     * PATCH /api/orders/{id}/receive
     */
    @PatchMapping("/{id}/receive")
    @Transactional
    public ResponseEntity<?> receiveOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Vui lòng đăng nhập"));
        }

        Order order = orderRepository.findById(id).orElse(null);
        if (order == null || order.getUser() == null || !order.getUser().getId().equals(principal.getUserId())) {
            return ResponseEntity.notFound().build();
        }

        if (order.getPaymentMethod() == null || !order.getPaymentMethod().equalsIgnoreCase("cash_on_delivery")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Chỉ áp dụng cho đơn thanh toán khi nhận hàng (COD)"));
        }

        // Mark as paid when customer confirms received.
        order.setStatus("paid");
        order = orderRepository.save(order);
        return ResponseEntity.ok(OrderDto.fromEntity(order));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<OrderDto>> getMyOrders(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "desc") String sortDir) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        List<OrderDto> orders = orderRepository.searchForUser(
                        principal.getUserId(),
                        status != null && !status.isBlank() ? status.trim() : null,
                        PageRequest.of(page, size, Sort.by(direction, "createdAt")))
                .getContent().stream()
                .map(OrderDto::fromEntity)
                .toList();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<OrderDto> getOrderById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Order order = orderOpt.get();
        if (!order.getUser().getId().equals(principal.getUserId())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(OrderDto.fromEntity(order));
    }
}
