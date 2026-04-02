package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.CouponDto;
import com.example.webdienthoai.dto.CreateCouponRequest;
import com.example.webdienthoai.entity.Coupon;
import com.example.webdienthoai.repository.CouponRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/coupons")
@RequiredArgsConstructor
public class AdminCouponsController {
    private final CouponRepository couponRepository;

    private static String normalizeDiscountType(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CouponDto>> listCoupons() {
        return ResponseEntity.ok(couponRepository.findAll().stream().map(CouponDto::fromEntity).toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createCoupon(@Valid @RequestBody CreateCouponRequest req) {
        String normalizedCode = req.getCode().trim().toUpperCase(Locale.ROOT);
        if (couponRepository.findByCodeIgnoreCase(normalizedCode).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Mã coupon đã tồn tại"));
        }
        String discountType = normalizeDiscountType(req.getDiscountType());
        if (!"percent".equals(discountType) && !"fixed".equals(discountType)) {
            return ResponseEntity.badRequest().body(Map.of("message", "discountType chỉ chấp nhận percent hoặc fixed"));
        }
        Coupon coupon = Coupon.builder()
                .code(normalizedCode)
                .discountType(discountType)
                .discountValue(req.getDiscountValue())
                .minOrderAmount(req.getMinOrderAmount())
                .maxDiscountAmount(req.getMaxDiscountAmount())
                .active(req.getActive() == null || req.getActive())
                .startsAt(req.getStartsAt())
                .endsAt(req.getEndsAt())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        coupon = couponRepository.save(coupon);
        return ResponseEntity.status(HttpStatus.CREATED).body(CouponDto.fromEntity(coupon));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateCoupon(@PathVariable Long id, @RequestBody CreateCouponRequest req) {
        Coupon coupon = couponRepository.findById(id).orElse(null);
        if (coupon == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (req.getCode() != null && !req.getCode().isBlank()) {
            String newCode = req.getCode().trim().toUpperCase(Locale.ROOT);
            Coupon existing = couponRepository.findByCodeIgnoreCase(newCode).orElse(null);
            if (existing != null && !existing.getId().equals(coupon.getId())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Mã coupon đã tồn tại"));
            }
            coupon.setCode(newCode);
        }
        if (req.getDiscountType() != null) {
            String discountType = normalizeDiscountType(req.getDiscountType());
            if (!"percent".equals(discountType) && !"fixed".equals(discountType)) {
                return ResponseEntity.badRequest().body(Map.of("message", "discountType chỉ chấp nhận percent hoặc fixed"));
            }
            coupon.setDiscountType(discountType);
        }
        if (req.getDiscountValue() != null) coupon.setDiscountValue(req.getDiscountValue());
        if (req.getMinOrderAmount() != null) coupon.setMinOrderAmount(req.getMinOrderAmount());
        if (req.getMaxDiscountAmount() != null) coupon.setMaxDiscountAmount(req.getMaxDiscountAmount());
        if (req.getActive() != null) coupon.setActive(req.getActive());
        if (req.getStartsAt() != null) coupon.setStartsAt(req.getStartsAt());
        if (req.getEndsAt() != null) coupon.setEndsAt(req.getEndsAt());
        coupon.setUpdatedAt(Instant.now());
        coupon = couponRepository.save(coupon);
        return ResponseEntity.ok(CouponDto.fromEntity(coupon));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deactivate(@PathVariable Long id) {
        Coupon coupon = couponRepository.findById(id).orElse(null);
        if (coupon == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        coupon.setActive(false);
        couponRepository.save(coupon);
        return ResponseEntity.ok(Map.of("message", "Đã ngừng kích hoạt coupon"));
    }
}
