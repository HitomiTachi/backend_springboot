package com.example.webdienthoai.repository;

import com.example.webdienthoai.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.order o JOIN FETCH o.user WHERE oi.id = :id")
    Optional<OrderItem> findByIdWithOrderAndUser(@Param("id") Long id);
}
