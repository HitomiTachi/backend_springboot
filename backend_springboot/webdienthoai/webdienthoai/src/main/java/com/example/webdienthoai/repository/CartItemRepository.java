package com.example.webdienthoai.repository;

import com.example.webdienthoai.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    Optional<CartItem> findByCart_UserIdAndProduct_Id(Long userId, Long productId);
}
