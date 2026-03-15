package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.ProductDto;
import com.example.webdienthoai.entity.Product;
import com.example.webdienthoai.repository.ProductRepository;
import com.example.webdienthoai.service.PhoneSpecsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductsController {

    private final ProductRepository productRepository;
    private final PhoneSpecsService phoneSpecsService;

    @GetMapping
    public ResponseEntity<List<ProductDto>> getProducts(
            @RequestParam(required = false) Long category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        List<Product> products;
        if ((category != null || (q != null && !q.isBlank()))) {
            products = productRepository.findByCategoryAndSearch(
                    category, (q != null && !q.isBlank()) ? q.trim() : null,
                    PageRequest.of(page, size)).getContent();
        } else {
            products = productRepository.findAll(PageRequest.of(page, size)).getContent();
        }

        List<ProductDto> dtos = products.stream()
                .map(ProductDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> getById(@PathVariable Long id) {
        return productRepository.findById(id)
                .map(ProductDto::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/featured")
    public ResponseEntity<List<ProductDto>> getFeatured() {
        List<ProductDto> list = productRepository.findByFeaturedTrue().stream()
                .map(ProductDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    /**
     * Gọi API ngoài (Zyla/Juhe/Apify) lấy thông số theo tên sản phẩm và lưu vào product.
     * Cần cấu hình app.phone-specs.api-url và app.phone-specs.api-key thì mới có hiệu lực.
     */
    @PostMapping("/{id}/fetch-specs")
    public ResponseEntity<?> fetchSpecs(@PathVariable Long id) {
        if (!productRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        boolean updated = phoneSpecsService.fetchAndSaveSpecs(id);
        if (!updated) {
            return ResponseEntity.accepted().body(
                    java.util.Map.of("message", "Chưa cấu hình API hoặc không tìm thấy dữ liệu. Xem PHONE_SPECS_APIS.md.")
            );
        }
        return productRepository.findById(id)
                .map(ProductDto::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok().build());
    }
}
