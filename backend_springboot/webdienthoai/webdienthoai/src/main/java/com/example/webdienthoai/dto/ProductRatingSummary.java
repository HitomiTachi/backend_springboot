package com.example.webdienthoai.dto;

/**
 * Điểm trung bình (1 chữ số thập phân) và số lượt đánh giá theo sản phẩm.
 */
public record ProductRatingSummary(double average, long count) {
}
