package com.example.webdienthoai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpsertProductRatingRequest {

    @NotNull(message = "orderItemId là bắt buộc")
    private Long orderItemId;

    @NotNull(message = "rating là bắt buộc")
    @Min(value = 1, message = "rating từ 1 đến 5")
    @Max(value = 5, message = "rating từ 1 đến 5")
    private Integer rating;
}
