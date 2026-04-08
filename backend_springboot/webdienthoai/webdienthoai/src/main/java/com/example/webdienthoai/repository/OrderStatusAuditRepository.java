package com.example.webdienthoai.repository;

import com.example.webdienthoai.entity.OrderStatusAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusAuditRepository extends JpaRepository<OrderStatusAudit, Long> {
    java.util.List<OrderStatusAudit> findByOrderIdOrderByChangedAtDesc(Long orderId);
    Page<OrderStatusAudit> findAllByOrderByChangedAtDesc(Pageable pageable);
}
