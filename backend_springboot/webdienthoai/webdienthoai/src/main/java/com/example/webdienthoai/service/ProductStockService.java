package com.example.webdienthoai.service;

import com.example.webdienthoai.entity.OrderItem;
import com.example.webdienthoai.entity.Product;
import com.example.webdienthoai.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.TreeMap;

/**
 * Hoàn kho khi hủy đơn / thanh toán thất bại: gom theo {@code productId}, khóa {@code PESSIMISTIC_WRITE}
 * theo thứ tự id tăng dần (cùng chiến lược với {@code OrdersController#createOrder}).
 */
@Service
@RequiredArgsConstructor
public class ProductStockService {

    private final ProductRepository productRepository;

    public void restockForOrderItems(Iterable<OrderItem> items) {
        if (items == null) {
            return;
        }
        Map<Long, Integer> qtyByProduct = new TreeMap<>();
        for (OrderItem item : items) {
            if (item == null || item.getProduct() == null) {
                continue;
            }
            Long pid = item.getProduct().getId();
            if (pid == null) {
                continue;
            }
            int q = item.getQuantity() != null ? item.getQuantity() : 0;
            if (q <= 0) {
                continue;
            }
            qtyByProduct.merge(pid, q, Integer::sum);
        }
        for (Map.Entry<Long, Integer> e : qtyByProduct.entrySet()) {
            int add = e.getValue();
            if (add <= 0) {
                continue;
            }
            Product p = productRepository.findByIdForUpdate(e.getKey()).orElse(null);
            if (p == null) {
                continue;
            }
            int stock = p.getStock() != null ? p.getStock() : 0;
            p.setStock(stock + add);
            productRepository.save(p);
        }
    }
}
