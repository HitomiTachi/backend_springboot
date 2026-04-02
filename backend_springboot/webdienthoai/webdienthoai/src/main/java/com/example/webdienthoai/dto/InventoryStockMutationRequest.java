package com.example.webdienthoai.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Optional;

@Data
public class InventoryStockMutationRequest {
    @NotNull
    private Long productId;

    @NotNull
    @Min(1)
    private Integer quantity;

    /**
     * Idempotency key (reserved for future phase).
     * Phase MVP: backend không enforce, chỉ accept để FE có thể gửi sẵn.
     */
    private String idempotencyKey;

    public Optional<String> getIdempotencyKeyOpt() {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return Optional.empty();
        return Optional.of(idempotencyKey.trim());
    }
}

