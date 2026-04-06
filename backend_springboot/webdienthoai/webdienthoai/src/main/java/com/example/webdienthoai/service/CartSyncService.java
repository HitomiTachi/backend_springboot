package com.example.webdienthoai.service;

import com.example.webdienthoai.entity.CartItem;
import com.example.webdienthoai.entity.OrderItem;
import com.example.webdienthoai.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Sau đặt hàng thành công: trừ đúng số lượng theo từng dòng đơn (product + màu + dung lượng),
 * thay vì xóa cả giỏ.
 */
@Service
@RequiredArgsConstructor
public class CartSyncService {

    private final CartRepository cartRepository;

    private static String normVariant(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean sameLine(CartItem ci, OrderItem oi) {
        if (ci.getProduct() == null || oi.getProduct() == null) {
            return false;
        }
        if (!ci.getProduct().getId().equals(oi.getProduct().getId())) {
            return false;
        }
        return Objects.equals(normVariant(ci.getSelectedColor()), normVariant(oi.getSelectedColor()))
                && Objects.equals(normVariant(ci.getSelectedStorage()), normVariant(oi.getSelectedStorage()));
    }

    public void removeOrderedQuantitiesFromCart(Long userId, List<OrderItem> orderItems) {
        if (userId == null || orderItems == null || orderItems.isEmpty()) {
            return;
        }
        cartRepository.findByUserId(userId).ifPresent(cart -> {
            if (cart.getItems() == null || cart.getItems().isEmpty()) {
                return;
            }
            boolean touched = false;
            for (OrderItem oi : orderItems) {
                if (oi.getProduct() == null) {
                    continue;
                }
                int need = oi.getQuantity() != null ? oi.getQuantity() : 0;
                if (need <= 0) {
                    continue;
                }
                Iterator<CartItem> it = cart.getItems().iterator();
                while (it.hasNext() && need > 0) {
                    CartItem ci = it.next();
                    if (!sameLine(ci, oi)) {
                        continue;
                    }
                    int have = ci.getQuantity() != null ? ci.getQuantity() : 0;
                    if (have <= need) {
                        need -= have;
                        it.remove();
                        touched = true;
                    } else {
                        ci.setQuantity(have - need);
                        need = 0;
                        touched = true;
                    }
                }
            }
            if (touched) {
                cartRepository.save(cart);
            }
        });
    }
}
