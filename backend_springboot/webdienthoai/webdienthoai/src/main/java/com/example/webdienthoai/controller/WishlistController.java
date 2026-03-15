package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.WishlistItemDto;
import com.example.webdienthoai.entity.Product;
import com.example.webdienthoai.entity.WishlistItem;
import com.example.webdienthoai.repository.ProductRepository;
import com.example.webdienthoai.repository.WishlistItemRepository;
import com.example.webdienthoai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getWishlist(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<WishlistItemDto> list = wishlistItemRepository.findByUserIdOrderByIdDesc(principal.getUserId()).stream()
                .map(WishlistItemDto::fromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }

    @PostMapping("/items")
    @Transactional
    public ResponseEntity<?> addItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> body) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Object productIdObj = body.get("productId");
        if (productIdObj == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "productId is required"));
        }
        Long productId = null;
        if (productIdObj instanceof Number) {
            productId = ((Number) productIdObj).longValue();
        } else if (productIdObj instanceof String) {
            try {
                productId = Long.parseLong((String) productIdObj);
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().body(Map.of("message", "productId must be a number"));
            }
        }
        if (productId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "productId is required"));
        }
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Product not found"));
        }
        if (wishlistItemRepository.existsByUserIdAndProduct_Id(principal.getUserId(), productId)) {
            List<WishlistItemDto> list = wishlistItemRepository.findByUserIdOrderByIdDesc(principal.getUserId()).stream()
                    .map(WishlistItemDto::fromEntity)
                    .toList();
            return ResponseEntity.ok(list);
        }
        WishlistItem item = WishlistItem.builder()
                .userId(principal.getUserId())
                .product(product)
                .build();
        wishlistItemRepository.save(item);
        List<WishlistItemDto> list = wishlistItemRepository.findByUserIdOrderByIdDesc(principal.getUserId()).stream()
                .map(WishlistItemDto::fromEntity)
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(list);
    }

    @DeleteMapping("/items/{productId}")
    @Transactional
    public ResponseEntity<?> removeItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long productId) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        wishlistItemRepository.deleteByUserIdAndProduct_Id(principal.getUserId(), productId);
        List<WishlistItemDto> list = wishlistItemRepository.findByUserIdOrderByIdDesc(principal.getUserId()).stream()
                .map(WishlistItemDto::fromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }
}
