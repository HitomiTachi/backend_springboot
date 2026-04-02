package com.example.webdienthoai.dto;

import com.example.webdienthoai.entity.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryDto {
    private Long id;
    private String name;
    private String slug;
    private Long parentId;
    private String description;

    public static CategoryDto fromEntity(Category c) {
        if (c == null) return null;
        return CategoryDto.builder()
                .id(c.getId())
                .name(c.getName())
                .slug(c.getSlug())
                .parentId(c.getParentId())
                .description(c.getDescription())
                .build();
    }
}
