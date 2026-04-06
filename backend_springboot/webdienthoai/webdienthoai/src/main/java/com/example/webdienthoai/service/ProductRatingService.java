package com.example.webdienthoai.service;

import com.example.webdienthoai.dto.ProductRatingSummary;
import com.example.webdienthoai.entity.Order;
import com.example.webdienthoai.entity.OrderItem;
import com.example.webdienthoai.entity.Product;
import com.example.webdienthoai.entity.ProductRating;
import com.example.webdienthoai.entity.User;
import com.example.webdienthoai.repository.OrderItemRepository;
import com.example.webdienthoai.repository.ProductRatingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductRatingService {

    private final OrderItemRepository orderItemRepository;
    private final ProductRatingRepository productRatingRepository;
    private final OrderStatusService orderStatusService;

    @Transactional(readOnly = true)
    public Map<Long, ProductRatingSummary> summariesByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProductRatingSummary> out = new HashMap<>();
        for (Object[] row : productRatingRepository.aggregateByProductIds(productIds)) {
            if (row == null || row.length < 3 || row[0] == null) {
                continue;
            }
            long pid = ((Number) row[0]).longValue();
            double rawAvg = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
            long cnt = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            out.put(pid, new ProductRatingSummary(roundOneDecimal(rawAvg), cnt));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Optional<ProductRatingSummary> summaryForProduct(Long productId) {
        if (productId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(summariesByProductIds(Collections.singletonList(productId)).get(productId));
    }

    /**
     * Tạo hoặc cập nhật đánh giá sao cho một dòng đơn. Chỉ chủ đơn; đơn phải {@code delivered} hoặc {@code completed}.
     */
    @Transactional
    public ProductRating upsertRating(Long userId, Long orderItemId, int rating) {
        if (userId == null || orderItemId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu thông tin đánh giá");
        }
        if (rating < 1 || rating > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rating từ 1 đến 5");
        }

        OrderItem orderItem = orderItemRepository.findByIdWithOrderAndUser(orderItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy dòng đơn hàng"));

        Order order = orderItem.getOrder();
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền đánh giá dòng đơn này");
        }

        String st = orderStatusService.normalize(order.getStatus());
        if (!"delivered".equals(st) && !"completed".equals(st)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ đánh giá sau khi đơn đã giao thành công");
        }

        Product product = orderItem.getProduct();
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dòng đơn không gắn sản phẩm, không thể đánh giá");
        }

        User user = order.getUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không tải được thông tin người dùng");
        }

        Optional<ProductRating> existing = productRatingRepository.findByOrderItem_Id(orderItemId);
        if (existing.isPresent()) {
            ProductRating pr = existing.get();
            if (!Objects.equals(pr.getUser().getId(), userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền sửa đánh giá này");
            }
            pr.setRating(rating);
            return productRatingRepository.save(pr);
        }

        ProductRating created = ProductRating.builder()
                .orderItem(orderItem)
                .user(user)
                .product(product)
                .rating(rating)
                .build();
        return productRatingRepository.save(created);
    }

    private static double roundOneDecimal(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
