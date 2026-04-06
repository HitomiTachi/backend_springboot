package com.example.webdienthoai.service;

import com.example.webdienthoai.entity.ProductPriceAudit;
import com.example.webdienthoai.repository.ProductPriceAuditRepository;
import com.example.webdienthoai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ProductPriceAuditService {

    private final ProductPriceAuditRepository productPriceAuditRepository;

    public static String formatActor(UserPrincipal principal) {
        if (principal == null) {
            return "unknown";
        }
        return "admin:" + principal.getUserId() + "(" + principal.getEmail() + ")";
    }

    /**
     * Ghi nhận thay đổi giá (tạo mới: {@code oldPrice} = null). Bỏ qua nếu giá không đổi.
     */
    public void recordPriceChange(Long productId, BigDecimal oldPrice, BigDecimal newPrice, String actor) {
        if (newPrice == null || actor == null || actor.isBlank()) {
            return;
        }
        if (oldPrice != null && oldPrice.compareTo(newPrice) == 0) {
            return;
        }
        productPriceAuditRepository.save(ProductPriceAudit.builder()
                .productId(productId)
                .oldPrice(oldPrice)
                .newPrice(newPrice)
                .actor(actor)
                .build());
    }
}
