package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.CreateProductRequest;
import com.example.webdienthoai.dto.ProductDto;
import com.example.webdienthoai.dto.ProductRatingSummary;
import com.example.webdienthoai.entity.Category;
import com.example.webdienthoai.entity.Product;
import com.example.webdienthoai.repository.CategoryRepository;
import com.example.webdienthoai.repository.ProductRepository;
import com.example.webdienthoai.service.ProductRatingService;
import com.example.webdienthoai.service.ProductSlugService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductsController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductSlugService productSlugService;
    private final ProductRatingService productRatingService;

    private static String trimToNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }

    /**
     * id gốc + mọi danh mục con (theo parent_id), BFS trên toàn bộ bảng categories.
     */
    private List<Long> categoryIdsIncludingDescendants(Long rootId) {
        List<Category> all = categoryRepository.findAll();
        Map<Long, List<Long>> childrenByParent = new HashMap<>();
        for (Category c : all) {
            Long pid = c.getParentId();
            childrenByParent.computeIfAbsent(pid, k -> new ArrayList<>()).add(c.getId());
        }
        List<Long> out = new ArrayList<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long id = queue.poll();
            out.add(id);
            List<Long> children = childrenByParent.get(id);
            if (children != null) {
                queue.addAll(children);
            }
        }
        return out;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProductDto>> getProducts(
            @RequestParam(required = false) Long category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean includeDescendants,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        List<Product> products;
        String qTrimmed = (q != null && !q.isBlank()) ? q.trim() : null;
        if (category != null || qTrimmed != null) {
            if (category != null && includeDescendants) {
                List<Long> ids = categoryIdsIncludingDescendants(category);
                if (ids.isEmpty()) {
                    products = List.of();
                } else {
                    products = productRepository.findByCategoryIdInAndSearch(ids, qTrimmed, pageable).getContent();
                }
            } else {
                products = productRepository.findByCategoryAndSearch(category, qTrimmed, pageable).getContent();
            }
        } else {
            products = productRepository.findAll(pageable).getContent();
        }

        return ResponseEntity.ok(mapProductsWithRatings(products));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ProductDto> getById(@PathVariable Long id) {
        return productRepository.findById(id)
                .map(p -> ProductDto.fromEntity(p, productRatingService.summariesByProductIds(
                        Set.of(p.getId())).get(p.getId())))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/slug/{slug}")
    @Transactional(readOnly = true)
    public ResponseEntity<ProductDto> getBySlug(@PathVariable String slug) {
        if (slug == null || slug.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        return productRepository.findBySlugIgnoreCase(slug.trim())
                .map(p -> ProductDto.fromEntity(p, productRatingService.summariesByProductIds(
                        Set.of(p.getId())).get(p.getId())))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/featured")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProductDto>> getFeatured() {
        List<Product> featured = productRepository.findByFeaturedTrue();
        return ResponseEntity.ok(mapProductsWithRatings(featured));
    }

    private List<ProductDto> mapProductsWithRatings(List<Product> products) {
        Set<Long> ids = products.stream()
                .map(Product::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ProductRatingSummary> summaryMap = productRatingService.summariesByProductIds(ids);
        return products.stream()
                .map(p -> ProductDto.fromEntity(p, summaryMap.get(p.getId())))
                .collect(Collectors.toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<?> createProduct(@Valid @RequestBody CreateProductRequest req) {
        Category category = categoryRepository.findById(req.getCategoryId()).orElse(null);
        if (category == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Danh mục không tồn tại"));
        }

        Product product = Product.builder()
                .name(req.getName())
                .slug(productSlugService.buildUniqueSlug(
                        req.getSlug() != null && !req.getSlug().isBlank() ? req.getSlug() : req.getName(), null))
                .description(req.getDescription())
                .image(req.getImage())
                .price(req.getPrice())
                .category(category)
                .categoryId(req.getCategoryId())
                .stock(req.getStock() != null ? req.getStock() : 0)
                .featured(req.getFeatured() != null && req.getFeatured())
                .colors(trimToNull(req.getColors()))
                .storageOptions(trimToNull(req.getStorageOptions()))
                .specifications(trimToNull(req.getSpecifications()))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        product = productRepository.save(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductDto.fromEntity(product));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<?> updateProduct(
            @PathVariable Long id,
            @RequestBody CreateProductRequest req) {
        Product product = productRepository.findById(id).orElse(null);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if (req.getName() != null) product.setName(req.getName());
        if (req.getSlug() != null || req.getName() != null) {
            String preferred = (req.getSlug() != null && !req.getSlug().isBlank()) ? req.getSlug() : product.getName();
            product.setSlug(productSlugService.buildUniqueSlug(preferred, product.getId()));
        }
        if (req.getDescription() != null) product.setDescription(req.getDescription());
        if (req.getImage() != null) product.setImage(req.getImage());
        if (req.getPrice() != null) product.setPrice(req.getPrice());
        if (req.getCategoryId() != null) {
            Category category = categoryRepository.findById(req.getCategoryId()).orElse(null);
            if (category == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Danh mục không tồn tại"));
            }
            product.setCategory(category);
            product.setCategoryId(req.getCategoryId());
        }
        if (req.getStock() != null) product.setStock(req.getStock());
        if (req.getFeatured() != null) product.setFeatured(req.getFeatured());
        if (req.getColors() != null) product.setColors(trimToNull(req.getColors()));
        if (req.getStorageOptions() != null) product.setStorageOptions(trimToNull(req.getStorageOptions()));
        if (req.getSpecifications() != null) product.setSpecifications(trimToNull(req.getSpecifications()));
        product.setUpdatedAt(Instant.now());

        product = productRepository.save(product);
        return ResponseEntity.ok(ProductDto.fromEntity(product));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        if (!productRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        productRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
