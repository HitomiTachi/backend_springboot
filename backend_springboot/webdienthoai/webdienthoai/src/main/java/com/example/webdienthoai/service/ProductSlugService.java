package com.example.webdienthoai.service;

import com.example.webdienthoai.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;

/**
 * Slug sản phẩm: đồng bộ giữa {@code ProductsController} và {@code AdminController}
 * (ưu tiên {@code slug} trong request nếu có, không thì theo {@code name}; đổi tên → slug tính lại).
 */
@Service
@RequiredArgsConstructor
public class ProductSlugService {

    private final ProductRepository productRepository;

    public static String slugify(String input) {
        if (input == null) {
            return "";
        }
        String normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-");
        return normalized;
    }

    public String buildUniqueSlug(String preferred, Long currentProductId) {
        String base = slugify(preferred);
        if (base.isBlank()) {
            base = "product-" + Instant.now().toEpochMilli();
        }
        String candidate = base;
        int seq = 1;
        while (true) {
            var existed = productRepository.findBySlugIgnoreCase(candidate).orElse(null);
            if (existed == null || (currentProductId != null && existed.getId().equals(currentProductId))) {
                return candidate;
            }
            candidate = base + "-" + seq++;
        }
    }
}
