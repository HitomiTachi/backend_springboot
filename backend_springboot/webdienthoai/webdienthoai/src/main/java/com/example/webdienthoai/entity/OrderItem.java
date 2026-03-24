package com.example.webdienthoai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_image")
    private String productImage;

    private Integer quantity;

    @Column(name = "product_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAtOrder;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "selected_color")
    private String selectedColor;

    @Column(name = "selected_storage")
    private String selectedStorage;

    @Column(updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.product != null) {
            if (this.productName == null) {
                this.productName = this.product.getName();
            }
            if (this.productImage == null) {
                this.productImage = this.product.getImage();
            }
        }
        if (this.lineTotal == null) {
            BigDecimal unitPrice = this.priceAtOrder != null ? this.priceAtOrder : BigDecimal.ZERO;
            int qty = this.quantity != null ? this.quantity : 0;
            this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
        }
        this.createdAt = Instant.now();
    }
}
