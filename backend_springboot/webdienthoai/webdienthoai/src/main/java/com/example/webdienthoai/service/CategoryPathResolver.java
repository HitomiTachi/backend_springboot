package com.example.webdienthoai.service;

import com.example.webdienthoai.entity.Category;
import com.example.webdienthoai.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Resolve đường dẫn danh mục dạng {@code Cha > Con > Cháu} (ký tự {@code >}) thành {@code categoryId}.
 */
@Service
@RequiredArgsConstructor
public class CategoryPathResolver {

    private final CategoryRepository categoryRepository;

    public Long resolve(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Danh mục trống");
        }
        String[] parts = path.split(">");
        Long parentId = null;
        StringBuilder prefix = new StringBuilder();
        for (String part : parts) {
            String name = part.trim();
            if (name.isEmpty()) {
                continue;
            }
            List<Category> found = categoryRepository.findByNameIgnoreCaseAndParentId(name, parentId);
            if (found.isEmpty()) {
                String ctx = prefix.length() > 0 ? " (sau \"" + prefix + "\")" : "";
                throw new IllegalArgumentException("Không tìm thấy danh mục \"" + name + "\"" + ctx);
            }
            if (found.size() > 1) {
                throw new IllegalArgumentException(
                        "Nhiều hơn một danh mục \"" + name + "\" cùng cấp — không xác định được (trùng tên)");
            }
            if (prefix.length() > 0) {
                prefix.append(" > ");
            }
            prefix.append(name);
            parentId = found.get(0).getId();
        }
        if (parentId == null) {
            throw new IllegalArgumentException("Đường dẫn danh mục không hợp lệ");
        }
        return parentId;
    }
}
