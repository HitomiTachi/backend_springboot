package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.CategoryDto;
import com.example.webdienthoai.dto.CreateCategoryRequest;
import com.example.webdienthoai.entity.Category;
import com.example.webdienthoai.repository.CategoryRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoriesController {

    private final CategoryRepository categoryRepository;

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

    private String buildUniqueSlug(String preferred, Long currentCategoryId) {
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

    @GetMapping
    public ResponseEntity<List<CategoryDto>> getAll() {
        List<CategoryDto> list = categoryRepository.findAll().stream()
                .map(CategoryDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryDto> getById(@PathVariable Long id) {
        return categoryRepository.findById(id)
                .map(CategoryDto::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createCategory(@Valid @RequestBody CreateCategoryRequest req) {
        if (req.getParentId() != null && !categoryRepository.existsById(req.getParentId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Danh mục cha không tồn tại"));
        }
        Category category = Category.builder()
                .name(req.getName())
                .slug(buildUniqueSlug(req.getSlug() != null && !req.getSlug().isBlank() ? req.getSlug() : req.getName(), null))
                .parentId(req.getParentId())
                .description(req.getDescription())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        category = categoryRepository.save(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoryDto.fromEntity(category));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<?> updateCategory(
            @PathVariable Long id,
            @RequestBody CreateCategoryRequest req) {
        Category category = categoryRepository.findById(id).orElse(null);
        if (category == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (req.getName() != null) category.setName(req.getName());
        if (req.getParentId() != null && !categoryRepository.existsById(req.getParentId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Danh mục cha không tồn tại"));
        }
        if (req.getParentId() != null && req.getParentId().equals(category.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Danh mục cha không hợp lệ"));
        }
        if (req.getParentId() != null) category.setParentId(req.getParentId());
        if (req.getSlug() != null || req.getName() != null) {
            String preferred = (req.getSlug() != null && !req.getSlug().isBlank()) ? req.getSlug() : category.getName();
            category.setSlug(buildUniqueSlug(preferred, category.getId()));
        }
        if (req.getDescription() != null) category.setDescription(req.getDescription());
        category.setUpdatedAt(Instant.now());
        category = categoryRepository.save(category);
        return ResponseEntity.ok(CategoryDto.fromEntity(category));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        if (!categoryRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        categoryRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
