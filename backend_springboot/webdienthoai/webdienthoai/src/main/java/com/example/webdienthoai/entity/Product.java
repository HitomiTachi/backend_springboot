package com.example.webdienthoai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 200, unique = true)
    private String slug;

    @Column(length = 1000)
    private String description;

    @Column(length = 500)
    private String image;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "category_id", insertable = false, updatable = false)
    private Long categoryId;

    private Integer stock;
    
    /**
     * Số lượng đang được reserve (đã giữ chỗ) cho các đơn chưa chốt sold.
     * Trong phase MVP này, reservedStock chỉ phục vụ module inventory; flow order hiện tại chưa dùng reserve/sold.
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer reservedStock = 0;

    private Boolean featured;

    /** JSON: [{ "name": "Đen", "hex": "#1d1d1f" }, ...] */
    @Column(columnDefinition = "TEXT")
    private String colors;

    /** JSON: ["128GB","256GB"] hoặc legacy [{ "capacity": "256GB", "price": ... }] */
    @Column(columnDefinition = "TEXT")
    private String storageOptions;

    /** JSON object thông số kỹ thuật (cấu trúc lồng như FE ProductDetail) */
    @Column(columnDefinition = "TEXT")
    private String specifications;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
