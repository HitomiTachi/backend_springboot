package com.example.webdienthoai.dto;

import com.example.webdienthoai.entity.Coupon;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class CouponDto {
    private Long id;
    private String code;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmount;
    private BigDecimal maxDiscountAmount;
    private Boolean active;
    private Instant startsAt;
    private Instant endsAt;

    public static CouponDto fromEntity(Coupon coupon) {
        if (coupon == null) return null;
        return CouponDto.builder()
                .id(coupon.getId())
                .code(coupon.getCode())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .minOrderAmount(coupon.getMinOrderAmount())
                .maxDiscountAmount(coupon.getMaxDiscountAmount())
                .active(coupon.getActive())
                .startsAt(coupon.getStartsAt())
                .endsAt(coupon.getEndsAt())
                .build();
    }
}
