package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.ProductRatingResponseDto;
import com.example.webdienthoai.dto.UpsertProductRatingRequest;
import com.example.webdienthoai.entity.ProductRating;
import com.example.webdienthoai.security.UserPrincipal;
import com.example.webdienthoai.service.ProductRatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/product-ratings")
@RequiredArgsConstructor
public class ProductRatingController {

    private final ProductRatingService productRatingService;

    /**
     * Tạo hoặc cập nhật đánh giá sao cho một {@code order_item} (đơn đã giao thành công).
     */
    @PutMapping
    @Transactional
    public ResponseEntity<ProductRatingResponseDto> upsert(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpsertProductRatingRequest body) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        ProductRating saved = productRatingService.upsertRating(
                principal.getUserId(),
                body.getOrderItemId(),
                body.getRating());
        return ResponseEntity.ok(ProductRatingResponseDto.fromEntity(saved));
    }
}
