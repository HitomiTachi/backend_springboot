package com.example.webdienthoai.repository;

import com.example.webdienthoai.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("""
            SELECT o FROM Order o
            WHERE (:status IS NULL OR LOWER(o.status) = LOWER(:status))
            """)
    Page<Order> searchForAdmin(@Param("status") String status, Pageable pageable);

    @Query("""
            SELECT o FROM Order o
            WHERE o.userId = :userId
              AND (:status IS NULL OR LOWER(o.status) = LOWER(:status))
            """)
    Page<Order> searchForUser(@Param("userId") Long userId, @Param("status") String status, Pageable pageable);

}
