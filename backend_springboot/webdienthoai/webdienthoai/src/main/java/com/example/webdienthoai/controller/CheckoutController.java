package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.CheckoutQuoteRequest;
import com.example.webdienthoai.dto.CheckoutQuoteResponse;
import com.example.webdienthoai.dto.OrderItemRequest;
import com.example.webdienthoai.entity.Cart;
import com.example.webdienthoai.entity.Product;
import com.example.webdienthoai.repository.CartRepository;
import com.example.webdienthoai.repository.ProductRepository;
import com.example.webdienthoai.security.UserPrincipal;
import com.example.webdienthoai.service.CouponDiscountService;
import com.example.webdienthoai.service.ShippingPricing;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
public class CheckoutController {
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final CouponDiscountService couponDiscountService;

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
                ir.setSelectedColor(ci.getSelectedColor());
                ir.setSelectedStorage(ci.getSelectedStorage());
                items.add(ir);
            }
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItemRequest item : items) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "Số lượng sản phẩm phải lớn hơn 0"));
            }
            Product product = productRepository.findById(item.getProductId()).orElse(null);
            if (product == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Sản phẩm không tồn tại: " + item.getProductId()));
            }
            BigDecimal unit = product.getPrice();
            subtotal = subtotal.add(unit.multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        String couponCode = req != null ? req.getCouponCode() : null;

        BigDecimal discount = BigDecimal.ZERO;
        boolean couponApplied = false;
        String couponMessage = null;

        if (couponCode != null && !couponCode.isBlank()) {
            try {
                CouponDiscountService.CouponApplyResult applied =
                        couponDiscountService.computeDiscountOrThrow(couponCode, subtotal);
                discount = applied.discount();
                if (applied.canonicalCode() != null) {
                    couponApplied = true;
                    couponMessage = "Áp dụng mã giảm giá thành công";
                    couponCode = applied.canonicalCode();
                }
            } catch (IllegalArgumentException ex) {
                couponMessage = ex.getMessage();
            }
        }

        BigDecimal netMerchandise = subtotal.subtract(discount);
        if (netMerchandise.compareTo(BigDecimal.ZERO) < 0) {
            netMerchandise = BigDecimal.ZERO;
        }
        BigDecimal shippingCost = ShippingPricing.computeForNetMerchandise(netMerchandise);

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
