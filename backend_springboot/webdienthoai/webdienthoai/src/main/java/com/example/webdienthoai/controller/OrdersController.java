package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.*;
import com.example.webdienthoai.entity.*;
import com.example.webdienthoai.repository.OrderRepository;
import com.example.webdienthoai.repository.ProductRepository;
import com.example.webdienthoai.repository.UserRepository;
import com.example.webdienthoai.repository.CartRepository;

import com.example.webdienthoai.security.UserPrincipal;
import com.example.webdienthoai.service.OrderStatusService;
import com.example.webdienthoai.service.ShippingPricing;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrdersController {

    private static String resolveInitialOrderStatus(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return "pending";
        }
        if ("cash_on_delivery".equalsIgnoreCase(paymentMethod)) {
            return "pending";
        }
        if ("vnpay".equalsIgnoreCase(paymentMethod)) {
            return "pending_payment";
        }
        return "paid";
    }

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;

    private final OrderStatusService orderStatusService;

    @PostMapping
    @Transactional
    public ResponseEntity<?> createOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateOrderRequest req) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Vui lòng đăng nhập"));
        }
        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        BigDecimal computedSubtotal = BigDecimal.ZERO;
        for (OrderItemRequest itemReq : req.getItems()) {
            if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "Số lượng sản phẩm phải lớn hơn 0"));
            }
            if (itemReq.getPrice() == null || itemReq.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "Giá sản phẩm không hợp lệ"));
            }
            computedSubtotal = computedSubtotal.add(
                    itemReq.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity())));
        }
        BigDecimal discount = Objects.requireNonNullElse(req.getDiscountAmount(), BigDecimal.ZERO);
        if (discount.compareTo(computedSubtotal) > 0) {
            discount = computedSubtotal;
        }
        BigDecimal netMerchandise = computedSubtotal.subtract(discount).max(BigDecimal.ZERO);
        BigDecimal shippingCost = ShippingPricing.computeForNetMerchandise(netMerchandise);
        BigDecimal totalPrice = computedSubtotal.subtract(discount).add(shippingCost);
        if (totalPrice.compareTo(BigDecimal.ZERO) < 0) {
            totalPrice = BigDecimal.ZERO;
        }

        Order order = Order.builder()
                .user(user)
            .shippingAddressId(req.getShippingAddressId())
            .subtotal(computedSubtotal)
            .discountAmount(discount)
            .shippingCost(shippingCost)
                .totalPrice(totalPrice)
            .paymentMethod(req.getPaymentMethod())
            .notes(req.getNotes())
            // COD => pending; VNPay => chờ redirect thanh toán; thẻ/PayPal (demo) => paid ngay
            .status(resolveInitialOrderStatus(req.getPaymentMethod()))
                .items(new ArrayList<>())
                .build();

        for (OrderItemRequest itemReq : req.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + itemReq.getProductId()));
            int currentStock = product.getStock() != null ? product.getStock() : 0;
            if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                throw new IllegalArgumentException("Số lượng sản phẩm phải lớn hơn 0");
            }
            if (currentStock < itemReq.getQuantity()) {
                throw new IllegalArgumentException("Sản phẩm không đủ tồn kho: " + product.getName());
            }
            product.setStock(currentStock - itemReq.getQuantity());
            productRepository.save(product);
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
        orderStatusService.changeStatus(order, order.getStatus(), "system", "Đơn hàng được tạo");
        order = orderRepository.save(order);

        // Đồng bộ hành vi checkout: sau khi đặt hàng thành công thì xóa cart server.
        cartRepository.findByUserId(principal.getUserId()).ifPresent(cart -> {
            if (cart.getItems() != null && !cart.getItems().isEmpty()) {
                cart.getItems().clear();
                cartRepository.save(cart);
            }
        });

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
        orderStatusService.changeStatus(order, "paid", "customer:" + principal.getUserId(), "Khách xác nhận nhận hàng COD");
        order = orderRepository.save(order);
        return ResponseEntity.ok(OrderDto.fromEntity(order));
    }

    @PatchMapping("/{id}/cancel")
    @Transactional
    public ResponseEntity<?> cancelOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Vui lòng đăng nhập"));
        }
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null || order.getUser() == null || !order.getUser().getId().equals(principal.getUserId())) {
            return ResponseEntity.notFound().build();
        }

        String status = orderStatusService.normalize(order.getStatus());
        if ("completed".equals(status) || "cancelled".equals(status)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Không thể hủy đơn ở trạng thái hiện tại"));
        }

        for (OrderItem item : order.getItems()) {
            Product p = item.getProduct();
            if (p != null) {
                int stock = p.getStock() != null ? p.getStock() : 0;
                p.setStock(stock + (item.getQuantity() != null ? item.getQuantity() : 0));
                productRepository.save(p);
            }
        }
        orderStatusService.changeStatus(order, "cancelled", "customer:" + principal.getUserId(), "Khách hủy đơn");
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
