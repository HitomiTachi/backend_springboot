package com.example.webdienthoai.repository;

import com.example.webdienthoai.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    java.util.Optional<Category> findBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCase(String slug);

    /** parentId null = danh mục gốc (parent_id IS NULL) */
    List<Category> findByNameIgnoreCaseAndParentId(String name, Long parentId);
}
