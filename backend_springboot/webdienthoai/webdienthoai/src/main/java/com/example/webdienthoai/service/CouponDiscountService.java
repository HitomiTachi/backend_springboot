package com.example.webdienthoai.service;

import com.example.webdienthoai.entity.Coupon;
import com.example.webdienthoai.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

/**
 * Tính giảm giá từ mã coupon — dùng chung cho {@code /api/checkout/quote} và {@code POST /api/orders}.
 */
@Service
@RequiredArgsConstructor
public class CouponDiscountService {

    public record CouponApplyResult(BigDecimal discount, String canonicalCode) {}

    private final CouponRepository couponRepository;

    /**
     * @param couponCode mã (có thể null/blank → không giảm)
     * @param subtotal   tổng tiền hàng trước giảm
     * @return số tiền giảm và mã lưu DB; {@code canonicalCode} null nếu không dùng mã
     * @throws IllegalArgumentException mã không hợp lệ / hết hạn / chưa đủ điều kiện
     */
    @Transactional(readOnly = true)
    public CouponApplyResult computeDiscountOrThrow(String couponCode, BigDecimal subtotal) {
        if (couponCode == null || couponCode.isBlank()) {
            return new CouponApplyResult(BigDecimal.ZERO, null);
        }
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) < 0) {
            subtotal = BigDecimal.ZERO;
        }

        String trimmed = couponCode.trim();
        Coupon coupon = couponRepository.findByCodeIgnoreCase(trimmed)
                .orElseThrow(() -> new IllegalArgumentException("Mã giảm giá không tồn tại"));

        if (!Boolean.TRUE.equals(coupon.getActive())) {
            throw new IllegalArgumentException("Mã giảm giá không hợp lệ hoặc đã ngừng áp dụng");
        }
        Instant now = Instant.now();
        if (coupon.getStartsAt() != null && now.isBefore(coupon.getStartsAt())) {
            throw new IllegalArgumentException("Mã giảm giá chưa đến thời gian áp dụng");
        }
        if (coupon.getEndsAt() != null && now.isAfter(coupon.getEndsAt())) {
            throw new IllegalArgumentException("Mã giảm giá đã hết hạn");
        }
        if (coupon.getMinOrderAmount() != null && subtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            throw new IllegalArgumentException("Đơn hàng chưa đạt giá trị tối thiểu để dùng mã");
        }

        String discountType = coupon.getDiscountType() != null
                ? coupon.getDiscountType().trim().toLowerCase(Locale.ROOT)
                : "";
        BigDecimal discount;
        if ("percent".equals(discountType)) {
            discount = subtotal.multiply(coupon.getDiscountValue()).divide(BigDecimal.valueOf(100));
        } else if ("fixed".equals(discountType)) {
            discount = coupon.getDiscountValue();
        } else {
            throw new IllegalArgumentException("Loại mã giảm giá không hợp lệ");
        }

        if (coupon.getMaxDiscountAmount() != null && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
            discount = coupon.getMaxDiscountAmount();
        }
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            discount = BigDecimal.ZERO;
        }
        return new CouponApplyResult(discount, coupon.getCode());
    }
}
