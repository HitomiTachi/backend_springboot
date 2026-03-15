package com.example.webdienthoai.repository;

import com.example.webdienthoai.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    List<WishlistItem> findByUserIdOrderByIdDesc(Long userId);

    boolean existsByUserIdAndProduct_Id(Long userId, Long productId);

    void deleteByUserIdAndProduct_Id(Long userId, Long productId);
}
