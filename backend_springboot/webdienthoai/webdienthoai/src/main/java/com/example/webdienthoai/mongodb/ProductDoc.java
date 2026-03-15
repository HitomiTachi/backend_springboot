package com.example.webdienthoai.mongodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "products")
public class ProductDoc {

    @Id
    private String id;
    private String name;
    private String slug;
    private String description;
    private String image;
    private BigDecimal price;
    private String categoryId;
    private String categoryName;
    private Integer stock;
    private Boolean featured;
    private String specifications;
}
