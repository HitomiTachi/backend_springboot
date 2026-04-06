package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.CategoryDto;
import com.example.webdienthoai.dto.CreateCategoryRequest;
import com.example.webdienthoai.dto.CreateProductRequest;
import com.example.webdienthoai.dto.ProductDto;
import com.example.webdienthoai.dto.ProductPriceAuditDto;
import com.example.webdienthoai.dto.UserDto;
import com.example.webdienthoai.entity.Category;
import com.example.webdienthoai.entity.Order;
import com.example.webdienthoai.entity.Product;
import com.example.webdienthoai.entity.User;
import com.example.webdienthoai.repository.CategoryRepository;
import com.example.webdienthoai.repository.OrderRepository;
import com.example.webdienthoai.repository.ProductPriceAuditRepository;
import com.example.webdienthoai.repository.ProductRepository;
import com.example.webdienthoai.repository.UserRepository;
import com.example.webdienthoai.security.UserPrincipal;
import com.example.webdienthoai.service.ProductPriceAuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderRepository orderRepository;
    private final ProductPriceAuditService productPriceAuditService;
    private final ProductPriceAuditRepository productPriceAuditRepository;

    private static String trimJsonField(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }

    private static String slugify(String input) {
        if (input == null) return "";
        return java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-");
    }

    private String buildUniqueCategorySlug(String preferred, Long currentCategoryId) {
        String base = slugify(preferred);
        if (base.isBlank()) {
            base = "category-" + Instant.now().toEpochMilli();
        }
        String candidate = base;
        int seq = 1;
        while (true) {
            var existed = categoryRepository.findBySlugIgnoreCase(candidate).orElse(null);
            if (existed == null || (currentCategoryId != null && existed.getId().equals(currentCategoryId))) {
                return candidate;
            }
            candidate = base + "-" + seq++;
        }
    }

    // ────── Stats ──────

    /** GET /api/admin/stats — tổng quan số liệu */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long totalProducts  = productRepository.count();
        long totalUsers     = userRepository.count();
        long totalOrders    = orderRepository.count();
        long totalCategories = categoryRepository.count();
        return ResponseEntity.ok(Map.of(
                "totalProducts",   totalProducts,
                "totalUsers",      totalUsers,
                "totalOrders",     totalOrders,
                "totalCategories", totalCategories
        ));
    }

    /**
     * GET /api/admin/dashboard/summary
     * MVP: tính revenue + số lượng đơn theo trạng thái + recent orders.
     *
     * Contract goal: FE có thể map tới `revenue`, `ordersByStatus`, `recentOrders`.
     */
    @GetMapping("/dashboard/summary")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getDashboardSummary(
            @RequestParam(defaultValue = "10") int recentLimit) {
        List<Order> orders = orderRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

        BigDecimal revenue = orders.stream()
                .filter(o -> o.getTotalPrice() != null)
                .filter(o -> {
                    String s = o.getStatus() != null ? o.getStatus().trim().toLowerCase() : "pending";
                    // Simple heuristic:
                    // - count paid/completed/shipping as revenue
                    // - subtract refunded orders
                    return "paid".equals(s) || "confirmed".equals(s) || "processing".equals(s)
                            || "shipping".equals(s) || "shipped".equals(s) || "delivered".equals(s)
                            || "completed".equals(s) || "returned".equals(s) || "refunded".equals(s);
                })
                .map(o -> {
                    String s = o.getStatus() != null ? o.getStatus().trim().toLowerCase() : "pending";
                    return "refunded".equals(s) ? o.getTotalPrice().negate() : o.getTotalPrice();
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> ordersByStatus = orders.stream()
                .collect(Collectors.groupingBy(
                        o -> o.getStatus() != null ? o.getStatus().trim().toLowerCase() : "pending",
                        Collectors.counting()
                ));

        List<Map<String, Object>> recentOrders = orders.stream()
                .limit(Math.max(0, recentLimit))
                .map(o -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", o.getId());
                    m.put("totalPrice", o.getTotalPrice());
                    m.put("status", o.getStatus());
                    m.put("createdAt", o.getCreatedAt());
                    m.put("customerName", o.getUser() != null ? o.getUser().getName() : null);
                    return m;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "revenue", revenue,
                "ordersByStatus", ordersByStatus,
                "recentOrders", recentOrders
        ));
    }

    // ────── Categories ──────

    /** Lấy tất cả danh mục (admin view) */
    @GetMapping("/categories")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CategoryDto>> getAllCategories() {
        return ResponseEntity.ok(
                categoryRepository.findAll().stream().map(CategoryDto::fromEntity).toList());
    }

    /** Tạo danh mục mới */
    @PostMapping("/categories")
    public ResponseEntity<?> createCategory(@Valid @RequestBody CreateCategoryRequest req) {
        if (req.getParentId() != null && !categoryRepository.existsById(req.getParentId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Danh mục cha không tồn tại"));
        }
        Category category = Category.builder()
                .name(req.getName())
                .slug(buildUniqueCategorySlug(
                        req.getSlug() != null && !req.getSlug().isBlank() ? req.getSlug() : req.getName(),
                        null))
                .parentId(req.getParentId())
                .description(req.getDescription())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        category = categoryRepository.save(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoryDto.fromEntity(category));
    }

    /** Cập nhật danh mục */
    @PatchMapping("/categories/{id}")
    @Transactional
    public ResponseEntity<?> updateCategory(
            @PathVariable Long id,
            @RequestBody CreateCategoryRequest req) {
        var category = categoryRepository.findById(id).orElse(null);
        if (category == null) return ResponseEntity.notFound().build();
        if (req.getName() != null && !req.getName().isBlank()) category.setName(req.getName());
        if (req.getParentId() != null && !categoryRepository.existsById(req.getParentId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Danh mục cha không tồn tại"));
        }
        if (req.getParentId() != null && req.getParentId().equals(category.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Danh mục cha không hợp lệ"));
        }
        if (req.getParentId() != null) category.setParentId(req.getParentId());
        if (req.getSlug() != null || req.getName() != null) {
            String preferred = (req.getSlug() != null && !req.getSlug().isBlank()) ? req.getSlug() : category.getName();
            category.setSlug(buildUniqueCategorySlug(preferred, category.getId()));
        }
        if (req.getDescription() != null) category.setDescription(req.getDescription());
        category.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(CategoryDto.fromEntity(categoryRepository.save(category)));
    }

    /** Xóa danh mục */
    @DeleteMapping("/categories/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        if (!categoryRepository.existsById(id)) return ResponseEntity.notFound().build();
        categoryRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ────── Products ──────

    /** Lấy danh sách tất cả sản phẩm */
    @GetMapping("/products")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProductDto>> getAllProducts() {
        return ResponseEntity.ok(
                productRepository.findAll().stream().map(ProductDto::fromEntity).toList());
    }

    /** Lịch sử thay đổi giá (audit) */
    @GetMapping("/products/{id}/price-audit")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getProductPriceAudit(@PathVariable Long id) {
        if (!productRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        var items = productPriceAuditRepository.findByProductIdOrderByChangedAtDesc(id).stream()
                .map(ProductPriceAuditDto::fromEntity)
                .toList();
        return ResponseEntity.ok(Map.of("items", items));
    }

    /** Tạo sản phẩm mới */
    @PostMapping("/products")
    @Transactional
    public ResponseEntity<?> createProduct(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateProductRequest req) {
        Category category = categoryRepository.findById(req.getCategoryId()).orElse(null);
        if (category == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Danh mục không tồn tại"));
        }
        Product product = Product.builder()
                .name(req.getName())
                .description(req.getDescription())
                .image(req.getImage())
                .price(req.getPrice())
                .category(category)
                .categoryId(req.getCategoryId())
                .stock(req.getStock() != null ? req.getStock() : 0)
                .featured(req.getFeatured() != null && req.getFeatured())
                .colors(trimJsonField(req.getColors()))
                .storageOptions(trimJsonField(req.getStorageOptions()))
                .specifications(trimJsonField(req.getSpecifications()))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        product = productRepository.save(product);
        String actor = ProductPriceAuditService.formatActor(principal);
        productPriceAuditService.recordPriceChange(product.getId(), null, product.getPrice(), actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductDto.fromEntity(product));
    }

    /** Cập nhật sản phẩm */
    @PatchMapping("/products/{id}")
    @Transactional
    public ResponseEntity<?> updateProduct(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestBody CreateProductRequest req) {
        Product product = productRepository.findById(id).orElse(null);
        if (product == null) return ResponseEntity.notFound().build();

        BigDecimal oldPrice = product.getPrice();
        if (req.getName() != null) product.setName(req.getName());
        if (req.getDescription() != null) product.setDescription(req.getDescription());
        if (req.getImage() != null) product.setImage(req.getImage());
        if (req.getPrice() != null) product.setPrice(req.getPrice());
        if (req.getCategoryId() != null) {
            Category category = categoryRepository.findById(req.getCategoryId()).orElse(null);
            if (category == null) return ResponseEntity.badRequest()
                    .body(Map.of("message", "Danh mục không tồn tại"));
            product.setCategory(category);
            product.setCategoryId(req.getCategoryId());
        }
        if (req.getStock() != null) product.setStock(req.getStock());
        if (req.getFeatured() != null) product.setFeatured(req.getFeatured());
        if (req.getColors() != null) product.setColors(trimJsonField(req.getColors()));
        if (req.getStorageOptions() != null) product.setStorageOptions(trimJsonField(req.getStorageOptions()));
        if (req.getSpecifications() != null) product.setSpecifications(trimJsonField(req.getSpecifications()));
        product.setUpdatedAt(Instant.now());
        product = productRepository.save(product);
        if (req.getPrice() != null) {
            productPriceAuditService.recordPriceChange(
                    product.getId(), oldPrice, product.getPrice(), ProductPriceAuditService.formatActor(principal));
        }
        return ResponseEntity.ok(ProductDto.fromEntity(product));
    }

    /** Xóa sản phẩm */
    @DeleteMapping("/products/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        if (!productRepository.existsById(id)) return ResponseEntity.notFound().build();
        productRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /** Lấy danh sách tất cả người dùng */
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getAllUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        var usersPage = userRepository.searchForAdmin(
                role != null && !role.isBlank() ? role.trim() : null,
                q != null && !q.isBlank() ? q.trim() : null,
                PageRequest.of(page, size, Sort.by(direction, sortBy)));
        List<UserDto> users = usersPage.getContent().stream()
                .map(UserDto::fromEntity)
                .toList();
        return ResponseEntity.ok(Map.of(
                "items", users,
                "page", usersPage.getNumber(),
                "size", usersPage.getSize(),
                "totalElements", usersPage.getTotalElements(),
                "totalPages", usersPage.getTotalPages()));
    }

    /** Lấy thông tin một người dùng theo ID */
    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(UserDto::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Thay đổi vai trò người dùng (admin / customer) */
    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> changeUserRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String newRole = body.get("role");
        if (newRole == null || (!newRole.equals("admin") && !newRole.equals("customer"))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Role không hợp lệ. Chỉ chấp nhận 'admin' hoặc 'customer'"));
        }
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Người dùng không tồn tại"));
        }
        user.setRole(newRole);
        userRepository.save(user);
        return ResponseEntity.ok(UserDto.fromEntity(user));
    }

    /** Xóa người dùng */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Người dùng không tồn tại"));
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Đã xóa người dùng"));
    }
}
