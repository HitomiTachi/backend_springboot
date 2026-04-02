package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.CheckoutQuoteRequest;
import com.example.webdienthoai.dto.CheckoutQuoteResponse;
import com.example.webdienthoai.dto.OrderItemRequest;
import com.example.webdienthoai.entity.Cart;
import com.example.webdienthoai.entity.Coupon;
import com.example.webdienthoai.repository.CartRepository;
import com.example.webdienthoai.repository.CouponRepository;
import com.example.webdienthoai.repository.ProductRepository;
import com.example.webdienthoai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
public class CheckoutController {
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;

    @PostMapping("/quote")
    @Transactional(readOnly = true)
    public ResponseEntity<?> quote(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) CheckoutQuoteRequest req) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<OrderItemRequest> items = req != null ? req.getItems() : null;
        if (items == null || items.isEmpty()) {
            Cart cart = cartRepository.findByUserId(principal.getUserId()).orElse(null);
            if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
                return ResponseEntity.ok(CheckoutQuoteResponse.builder()
                        .subtotal(BigDecimal.ZERO)
                        .discountAmount(BigDecimal.ZERO)
                        .shippingCost(BigDecimal.ZERO)
                        .totalPrice(BigDecimal.ZERO)
                        .couponApplied(false)
                        .couponMessage("Giỏ hàng trống")
                        .build());
            }
            items = new ArrayList<>();
            for (var ci : cart.getItems()) {
                OrderItemRequest ir = new OrderItemRequest();
                ir.setProductId(ci.getProduct().getId());
                ir.setQuantity(ci.getQuantity());
                ir.setPrice(ci.getPriceAtAdd());
                items.add(ir);
            }
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItemRequest item : items) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "Số lượng sản phẩm phải lớn hơn 0"));
            }
            if (item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "Giá sản phẩm không hợp lệ"));
            }
            var product = productRepository.findById(item.getProductId()).orElse(null);
            if (product == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Sản phẩm không tồn tại: " + item.getProductId()));
            }
            subtotal = subtotal.add(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        BigDecimal shippingCost = req != null && req.getShippingCost() != null ? req.getShippingCost() : BigDecimal.ZERO;
        String couponCode = req != null ? req.getCouponCode() : null;

        BigDecimal discount = BigDecimal.ZERO;
        boolean couponApplied = false;
        String couponMessage = null;

        if (couponCode != null && !couponCode.isBlank()) {
            Coupon coupon = couponRepository.findByCodeIgnoreCase(couponCode.trim()).orElse(null);
            if (coupon == null || !Boolean.TRUE.equals(coupon.getActive())) {
                couponMessage = "Mã giảm giá không hợp lệ hoặc đã ngừng áp dụng";
            } else if (coupon.getStartsAt() != null && Instant.now().isBefore(coupon.getStartsAt())) {
                couponMessage = "Mã giảm giá chưa đến thời gian áp dụng";
            } else if (coupon.getEndsAt() != null && Instant.now().isAfter(coupon.getEndsAt())) {
                couponMessage = "Mã giảm giá đã hết hạn";
            } else if (coupon.getMinOrderAmount() != null && subtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
                couponMessage = "Đơn hàng chưa đạt giá trị tối thiểu để dùng mã";
            } else {
                String discountType = coupon.getDiscountType() != null ? coupon.getDiscountType().trim().toLowerCase(Locale.ROOT) : "";
                if ("percent".equals(discountType)) {
                    discount = subtotal.multiply(coupon.getDiscountValue()).divide(BigDecimal.valueOf(100));
                } else if ("fixed".equals(discountType)) {
                    discount = coupon.getDiscountValue();
                } else {
                    return ResponseEntity.badRequest().body(Map.of("message", "Loại coupon không hợp lệ"));
                }
                if (coupon.getMaxDiscountAmount() != null && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
                    discount = coupon.getMaxDiscountAmount();
                }
                if (discount.compareTo(subtotal) > 0) {
                    discount = subtotal;
                }
                couponApplied = true;
                couponMessage = "Áp dụng mã giảm giá thành công";
                couponCode = coupon.getCode();
            }
        }

        BigDecimal totalPrice = subtotal.subtract(discount).add(shippingCost);
        if (totalPrice.compareTo(BigDecimal.ZERO) < 0) {
            totalPrice = BigDecimal.ZERO;
        }

        return ResponseEntity.ok(CheckoutQuoteResponse.builder()
                .subtotal(subtotal)
                .discountAmount(discount)
                .shippingCost(shippingCost)
                .totalPrice(totalPrice)
                .couponCode(couponCode)
                .couponApplied(couponApplied)
                .couponMessage(couponMessage)
                .build());
    }
}
