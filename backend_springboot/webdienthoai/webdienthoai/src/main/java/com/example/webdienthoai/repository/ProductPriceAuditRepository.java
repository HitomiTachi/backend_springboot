package com.example.webdienthoai.repository;

import com.example.webdienthoai.entity.ProductPriceAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductPriceAuditRepository extends JpaRepository<ProductPriceAudit, Long> {

    List<ProductPriceAudit> findByProductIdOrderByChangedAtDesc(Long productId);
}
