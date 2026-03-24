package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.AddCartItemRequest;
import com.example.webdienthoai.dto.CartDto;
import com.example.webdienthoai.entity.Cart;
import com.example.webdienthoai.entity.CartItem;
import com.example.webdienthoai.entity.Product;
import com.example.webdienthoai.repository.CartItemRepository;
import com.example.webdienthoai.repository.CartRepository;
import com.example.webdienthoai.repository.ProductRepository;
import com.example.webdienthoai.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    /**
     * Lấy giỏ hàng của user hiện tại
     */
    @GetMapping
    @Transactional
    public ResponseEntity<?> getCart(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        Cart cart = cartRepository.findByUserId(principal.getUserId())
                .orElseGet(() -> {
                    Cart newCart = Cart.builder()
                            .userId(principal.getUserId())
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();
                    return cartRepository.save(newCart);
                });
        
        return ResponseEntity.ok(CartDto.fromEntity(cart));
    }

    /**
     * Thêm sản phẩm vào giỏ hàng
     */
    @PostMapping("/items")
    @Transactional
    public ResponseEntity<?> addItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AddCartItemRequest req) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Lấy hoặc tạo giỏ hàng
        Cart cart = cartRepository.findByUserId(principal.getUserId())
                .orElseGet(() -> {
                    Cart newCart = Cart.builder()
                            .userId(principal.getUserId())
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();
                    return cartRepository.save(newCart);
                });

        // Kiểm tra sản phẩm tồn tại
        Product product = productRepository.findById(req.getProductId())
                .orElse(null);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Sản phẩm không tồn tại"));
        }

        // Kiểm tra xem sản phẩm đã có trong giỏ chưa
        CartItem existingItem = cartItemRepository
                .findByCart_UserIdAndProduct_Id(principal.getUserId(), req.getProductId())
                .orElse(null);

        // Lấy variant: ưu tiên selectedColor/selectedStorage, fallback về variant chung
        String resolvedColor = req.getSelectedColor() != null ? req.getSelectedColor() : req.getVariant();
        String resolvedStorage = req.getSelectedStorage();

        if (existingItem != null) {
            existingItem.setQuantity(existingItem.getQuantity() + req.getQuantity());
            if (resolvedColor != null) existingItem.setSelectedColor(resolvedColor);
            if (resolvedStorage != null) existingItem.setSelectedStorage(resolvedStorage);
            existingItem.setUpdatedAt(Instant.now());
            cartItemRepository.save(existingItem);
        } else {
            CartItem cartItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(req.getQuantity())
                    .selectedColor(resolvedColor)
                    .selectedStorage(resolvedStorage)
                    .priceAtAdd(product.getPrice())
                    .build();
            if (cart.getItems() == null) {
                cart.setItems(new java.util.ArrayList<>());
            }
            cart.getItems().add(cartItem);
            cartRepository.save(cart);
        }

        cart.setUpdatedAt(Instant.now());
        cartRepository.save(cart);
        
        return ResponseEntity.ok(CartDto.fromEntity(cart));
    }

    /**
     * Cập nhật số lượng item trong giỏ
     */
    @PatchMapping("/items/{itemId}")
    @Transactional
    public ResponseEntity<?> updateItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long itemId,
            @RequestBody Map<String, Object> body) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        CartItem item = cartItemRepository.findById(itemId).orElse(null);
        if (item == null || !item.getCart().getUserId().equals(principal.getUserId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Cập nhật số lượng
        if (body.containsKey("quantity")) {
            Object quantityObj = body.get("quantity");
            Integer quantity = null;
            if (quantityObj instanceof Number) {
                quantity = ((Number) quantityObj).intValue();
            } else if (quantityObj instanceof String) {
                try {
                    quantity = Integer.parseInt((String) quantityObj);
                } catch (NumberFormatException e) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "Số lượng phải là số"));
                }
            }
            
            if (quantity != null && quantity > 0) {
                item.setQuantity(quantity);
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Số lượng phải lớn hơn 0"));
            }
        }

        // Cập nhật màu hoặc dung lượng
        if (body.containsKey("selectedColor")) {
            item.setSelectedColor((String) body.get("selectedColor"));
        }
        if (body.containsKey("selectedStorage")) {
            item.setSelectedStorage((String) body.get("selectedStorage"));
        }

        item.setUpdatedAt(Instant.now());
        cartItemRepository.save(item);

        Cart cart = item.getCart();
        cart.setUpdatedAt(Instant.now());
        cartRepository.save(cart);

        return ResponseEntity.ok(CartDto.fromEntity(cart));
    }

    /**
     * Xóa item khỏi giỏ hàng
     */
    @DeleteMapping("/items/{itemId}")
    @Transactional
    public ResponseEntity<?> removeItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long itemId) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        CartItem item = cartItemRepository.findById(itemId).orElse(null);
        if (item == null || !item.getCart().getUserId().equals(principal.getUserId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Cart cart = item.getCart();
        cart.getItems().remove(item);
        cartItemRepository.deleteById(itemId);
        cart.setUpdatedAt(Instant.now());
        cartRepository.save(cart);

        return ResponseEntity.ok(CartDto.fromEntity(cart));
    }

    /**
     * Xóa tất cả items trong giỏ hàng
     */
    @DeleteMapping("/items")
    @Transactional
    public ResponseEntity<?> clearCart(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Cart cart = cartRepository.findByUserId(principal.getUserId()).orElse(null);
        if (cart == null) {
            return ResponseEntity.ok(Map.of("message", "Giỏ hàng trống"));
        }

        cart.getItems().clear();
        cart.setUpdatedAt(Instant.now());
        cartRepository.save(cart);

        return ResponseEntity.ok(CartDto.fromEntity(cart));
    }
}
