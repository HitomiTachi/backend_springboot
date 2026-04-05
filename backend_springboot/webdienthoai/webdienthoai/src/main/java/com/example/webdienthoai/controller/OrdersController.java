package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.*;
import com.example.webdienthoai.entity.*;
import com.example.webdienthoai.repository.AddressRepository;
import com.example.webdienthoai.repository.OrderRepository;
import com.example.webdienthoai.repository.ProductRepository;
import com.example.webdienthoai.repository.UserRepository;
import com.example.webdienthoai.repository.CartRepository;
import com.example.webdienthoai.repository.ShipmentRepository;
import com.example.webdienthoai.repository.OrderStatusAuditRepository;
import com.example.webdienthoai.repository.ReturnRequestRepository;

import com.example.webdienthoai.security.UserPrincipal;
import com.example.webdienthoai.service.CouponDiscountService;
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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private static boolean moneyEquals(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.setScale(2, RoundingMode.HALF_UP).compareTo(b.setScale(2, RoundingMode.HALF_UP)) == 0;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final ShipmentRepository shipmentRepository;
    private final AddressRepository addressRepository;
    private final CouponDiscountService couponDiscountService;

    private final OrderStatusService orderStatusService;
    private final OrderStatusAuditRepository orderStatusAuditRepository;
    private final ReturnRequestRepository returnRequestRepository;

    private OrderDto toOrderDto(Order order) {
        if (order == null || order.getId() == null) {
            return OrderDto.fromEntity(order, null);
        }
        return OrderDto.fromEntity(order, shipmentRepository.findByOrderId(order.getId()).orElse(null));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateOrderRequest req) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Vui lòng đăng nhập"));
        }
        try {
            User user = userRepository.findById(principal.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

            if (req.getShippingAddressId() == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng chọn địa chỉ giao hàng"));
            }
            Address shipping = addressRepository.findById(req.getShippingAddressId())
                    .orElseThrow(() -> new IllegalArgumentException("Địa chỉ giao hàng không tồn tại"));
            if (!shipping.getUserId().equals(principal.getUserId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Không có quyền dùng địa chỉ này"));
            }

            List<OrderItemRequest> sortedItems = new ArrayList<>(req.getItems());
            sortedItems.sort(Comparator.comparing(OrderItemRequest::getProductId));

            BigDecimal computedSubtotal = BigDecimal.ZERO;
            Order order = Order.builder()
                    .user(user)
                    .shippingAddressId(req.getShippingAddressId())
                    .paymentMethod(req.getPaymentMethod())
                    .notes(req.getNotes())
                    .status(resolveInitialOrderStatus(req.getPaymentMethod()))
                    .items(new ArrayList<>())
                    .build();

            for (OrderItemRequest itemReq : sortedItems) {
                if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Số lượng sản phẩm phải lớn hơn 0"));
                }

                Product product = productRepository.findByIdForUpdate(itemReq.getProductId())
                        .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại: " + itemReq.getProductId()));

                int currentStock = product.getStock() != null ? product.getStock() : 0;
                if (currentStock < itemReq.getQuantity()) {
                    throw new IllegalArgumentException("Sản phẩm không đủ tồn kho: " + product.getName());
                }
                product.setStock(currentStock - itemReq.getQuantity());
                productRepository.save(product);

                BigDecimal unitPrice = product.getPrice();
                BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));
                computedSubtotal = computedSubtotal.add(lineTotal);

                OrderItem item = OrderItem.builder()
                        .order(order)
                        .product(product)
                        .productName(product.getName())
                        .productImage(product.getImage())
                        .quantity(itemReq.getQuantity())
                        .priceAtOrder(unitPrice)
                        .lineTotal(lineTotal)
                        .selectedColor(trimToNull(itemReq.getSelectedColor()))
                        .selectedStorage(trimToNull(itemReq.getSelectedStorage()))
                        .build();
                order.getItems().add(item);
            }

            BigDecimal clientDiscount = Objects.requireNonNullElse(req.getDiscountAmount(), BigDecimal.ZERO);
            if (clientDiscount.compareTo(BigDecimal.ZERO) < 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "Số tiền giảm giá không hợp lệ"));
            }
            String couponRaw = req.getCouponCode();
            if (clientDiscount.compareTo(BigDecimal.ZERO) > 0
                    && (couponRaw == null || couponRaw.isBlank())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Có giảm giá nhưng không có mã coupon hợp lệ — vui lòng nhập mã hoặc tải lại trang"));
            }

            CouponDiscountService.CouponApplyResult couponApply =
                    couponDiscountService.computeDiscountOrThrow(couponRaw, computedSubtotal);
            BigDecimal discount = couponApply.discount();
            if (!moneyEquals(clientDiscount, discount)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Tổng giảm giá không khớp với mã áp dụng. Vui lòng tải lại bước thanh toán."));
            }

            BigDecimal netMerchandise = computedSubtotal.subtract(discount).max(BigDecimal.ZERO);
            BigDecimal shippingCost = ShippingPricing.computeForNetMerchandise(netMerchandise);
            BigDecimal totalPrice = computedSubtotal.subtract(discount).add(shippingCost);
            if (totalPrice.compareTo(BigDecimal.ZERO) < 0) {
                totalPrice = BigDecimal.ZERO;
            }

            if (req.getSubtotal() != null && !moneyEquals(req.getSubtotal(), computedSubtotal)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Tổng tiền hàng không khớp. Vui lòng tải lại."));
            }
            if (req.getShippingCost() != null && !moneyEquals(req.getShippingCost(), shippingCost)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Phí vận chuyển không khớp. Vui lòng tải lại."));
            }
            if (!moneyEquals(req.getTotalPrice(), totalPrice)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Tổng thanh toán không khớp. Vui lòng tải lại."));
            }

            order.setSubtotal(computedSubtotal);
            order.setDiscountAmount(discount);
            order.setShippingCost(shippingCost);
            order.setTotalPrice(totalPrice);
            order.setCouponCode(couponApply.canonicalCode());

            order = orderRepository.save(order);
            orderStatusService.changeStatus(order, order.getStatus(), "system", "Đơn hàng được tạo");
            order = orderRepository.save(order);

            boolean vnpay = "vnpay".equalsIgnoreCase(String.valueOf(req.getPaymentMethod()));
            if (!vnpay) {
                cartRepository.findByUserId(principal.getUserId()).ifPresent(cart -> {
                    if (cart.getItems() != null && !cart.getItems().isEmpty()) {
                        cart.getItems().clear();
                        cartRepository.save(cart);
                    }
                });
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(toOrderDto(order));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
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

        String st = orderStatusService.normalize(order.getStatus());
        if (!"shipped".equals(st) && !"shipping".equals(st)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Chỉ xác nhận nhận hàng khi đơn đã giao cho đơn vị vận chuyển"));
        }

        orderStatusService.changeStatus(order, "delivered", "customer:" + principal.getUserId(), "Khách xác nhận đã nhận hàng (COD)");
        orderStatusService.changeStatus(order, "completed", "customer:" + principal.getUserId(), "Hoàn tất đơn COD");
        order = orderRepository.save(order);
        return ResponseEntity.ok(toOrderDto(order));
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

        if (!orderStatusService.canCustomerCancel(order.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Chỉ có thể hủy đơn khi đơn đang chờ xác nhận hoặc chờ thanh toán"));
        }

        String st = orderStatusService.normalize(order.getStatus());
        if ("vnpay".equalsIgnoreCase(String.valueOf(order.getPaymentMethod())) && "pending_payment".equals(st)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message",
                            "Đơn VNPay đang chờ thanh toán — không hủy từ đây. Bỏ qua phiên thanh toán hoặc liên hệ hỗ trợ."));
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
        return ResponseEntity.ok(toOrderDto(order));
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
                .map(this::toOrderDto)
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
        return ResponseEntity.ok(toOrderDto(order));
    }

    @GetMapping("/{id}/status-history")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMyOrderStatusHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null || order.getUser() == null || !order.getUser().getId().equals(principal.getUserId())) {
            return ResponseEntity.notFound().build();
        }
        List<OrderStatusHistoryDto> items = orderStatusAuditRepository.findByOrderIdOrderByChangedAtDesc(id)
                .stream()
                .map(OrderStatusHistoryDto::fromEntity)
                .toList();
        return ResponseEntity.ok(Map.of("items", items));
    }

    @GetMapping("/{id}/returns")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMyOrderReturns(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null || order.getUser() == null || !order.getUser().getId().equals(principal.getUserId())) {
            return ResponseEntity.notFound().build();
        }
        List<ReturnRequestDto> items = returnRequestRepository.findByOrderIdOrderByCreatedAtDesc(id)
                .stream()
                .map(ReturnRequestDto::fromEntity)
                .toList();
        return ResponseEntity.ok(Map.of("items", items));
    }

    @PostMapping("/{id}/returns")
    @Transactional
    public ResponseEntity<?> createMyOrderReturn(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestBody CreateReturnRequest req) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Vui lòng đăng nhập"));
        }
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null || order.getUser() == null || !order.getUser().getId().equals(principal.getUserId())) {
            return ResponseEntity.notFound().build();
        }
        ReturnRequest rr = ReturnRequest.builder()
                .orderId(id)
                .status("requested")
                .reason(req.getReason())
                .refundAmount(req.getRefundAmount())
                .note(req.getNote())
                .restocked(false)
                .build();
        rr = returnRequestRepository.save(rr);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReturnRequestDto.fromEntity(rr));
    }


}
