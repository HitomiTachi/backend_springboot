package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.CategoryDto;
import com.example.webdienthoai.dto.CreateCategoryRequest;
import com.example.webdienthoai.dto.CreateProductRequest;
import com.example.webdienthoai.dto.ProductDto;
import com.example.webdienthoai.dto.UserDto;
import com.example.webdienthoai.entity.Category;
import com.example.webdienthoai.entity.Product;
import com.example.webdienthoai.entity.User;
import com.example.webdienthoai.repository.CategoryRepository;
import com.example.webdienthoai.repository.OrderRepository;
import com.example.webdienthoai.repository.ProductRepository;
import com.example.webdienthoai.repository.UserRepository;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderRepository orderRepository;

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
        Category category = Category.builder()
                .name(req.getName())
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

    /** Tạo sản phẩm mới */
    @PostMapping("/products")
    @Transactional
    public ResponseEntity<?> createProduct(@Valid @RequestBody CreateProductRequest req) {
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
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        product = productRepository.save(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductDto.fromEntity(product));
    }

    /** Cập nhật sản phẩm */
    @PatchMapping("/products/{id}")
    @Transactional
    public ResponseEntity<?> updateProduct(
            @PathVariable Long id,
            @RequestBody CreateProductRequest req) {
        Product product = productRepository.findById(id).orElse(null);
        if (product == null) return ResponseEntity.notFound().build();

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
        product.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(ProductDto.fromEntity(productRepository.save(product)));
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
