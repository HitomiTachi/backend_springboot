package com.example.webdienthoai.service;

import java.math.BigDecimal;

/**
 * Phí vận chuyển cố định: đơn có giá trị hàng (sau giảm giá) từ 500.000đ trở lên được miễn phí,
 * ngược lại phí 30.000đ.
 */
public final class ShippingPricing {

    public static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("500000");
    public static final BigDecimal FLAT_FEE = new BigDecimal("30000");

    private ShippingPricing() {}

    /**
     * @param netMerchandiseAfterDiscount tạm tính trừ giảm giá (không gồm phí ship)
     */
    public static BigDecimal computeForNetMerchandise(BigDecimal netMerchandiseAfterDiscount) {
        if (netMerchandiseAfterDiscount == null) {
            return FLAT_FEE;
        }
        BigDecimal net = netMerchandiseAfterDiscount.max(BigDecimal.ZERO);
        if (net.compareTo(FREE_SHIPPING_THRESHOLD) >= 0) {
            return BigDecimal.ZERO;
        }
        return FLAT_FEE;
    }
}
