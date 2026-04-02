package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.InventoryStockDto;
import com.example.webdienthoai.dto.InventoryStockMutationRequest;
import com.example.webdienthoai.entity.InventoryIdempotencyRecord;
import com.example.webdienthoai.entity.Product;
import com.example.webdienthoai.repository.InventoryIdempotencyRecordRepository;
import com.example.webdienthoai.repository.ProductRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/inventory")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminInventoryController {
    private final ProductRepository productRepository;
    private final InventoryIdempotencyRecordRepository inventoryIdempotencyRecordRepository;

    private ResponseEntity<?> handleIdempotencyReplayOrConflict(
            String operation,
            InventoryStockMutationRequest req,
            Product product) {
        Optional<String> idempotencyKeyOpt = req.getIdempotencyKeyOpt();
        if (idempotencyKeyOpt.isEmpty()) {
            return null;
        }
        String key = idempotencyKeyOpt.get();
        InventoryIdempotencyRecord existing = inventoryIdempotencyRecordRepository.findByIdempotencyKey(key).orElse(null);
        if (existing == null) {
            return null;
        }
        boolean sameRequest = existing.getOperation().equals(operation)
                && existing.getProductId().equals(req.getProductId())
                && existing.getQuantity().equals(req.getQuantity());
        if (!sameRequest) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Idempotency key đã được dùng cho request khác"));
        }
        return ResponseEntity.ok(Map.of(
                "idempotentReplay", true,
                "operation", operation,
                "stock", toDto(product)
        ));
    }

    private void persistIdempotency(String operation, InventoryStockMutationRequest req) {
        req.getIdempotencyKeyOpt().ifPresent(key -> inventoryIdempotencyRecordRepository.save(
                InventoryIdempotencyRecord.builder()
                        .idempotencyKey(key)
                        .operation(operation)
                        .productId(req.getProductId())
                        .quantity(req.getQuantity())
                        .build()
        ));
    }

    private InventoryStockDto toDto(Product p) {
        Integer stock = p.getStock() != null ? p.getStock() : 0;
        Integer reserved = p.getReservedStock() != null ? p.getReservedStock() : 0;
        return InventoryStockDto.builder()
                .productId(p.getId())
                .stock(stock)
                .reservedStock(reserved)
                .availableStock(stock) // in current MVP, `stock` is treated as available
                .build();
    }

    @GetMapping("/products/{productId}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getStock(@PathVariable Long productId) {
        return productRepository.findById(productId)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping("/add")
    @Transactional
    public ResponseEntity<?> add(@Valid @RequestBody InventoryStockMutationRequest req) {
        return productRepository.findById(req.getProductId())
                .map(p -> {
                    int stock = p.getStock() != null ? p.getStock() : 0;
                    p.setStock(stock + req.getQuantity());
                    return ResponseEntity.ok(toDto(productRepository.save(p)));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping("/remove")
    @Transactional
    public ResponseEntity<?> remove(@Valid @RequestBody InventoryStockMutationRequest req) {
        return productRepository.findById(req.getProductId())
                .map(p -> {
                    int stock = p.getStock() != null ? p.getStock() : 0;
                    if (stock < req.getQuantity()) {
                        return ResponseEntity.badRequest().body(Map.of("message", "Không đủ tồn kho để remove"));
                    }
                    p.setStock(stock - req.getQuantity());
                    return ResponseEntity.ok(toDto(productRepository.save(p)));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping("/reserve")
    @Transactional
    public ResponseEntity<?> reserve(@Valid @RequestBody InventoryStockMutationRequest req) {
        return productRepository.findById(req.getProductId())
                .map(p -> {
                    ResponseEntity<?> replay = handleIdempotencyReplayOrConflict("reserve", req, p);
                    if (replay != null) {
                        return replay;
                    }
                    int stock = p.getStock() != null ? p.getStock() : 0;
                    int reserved = p.getReservedStock() != null ? p.getReservedStock() : 0;
                    if (stock < req.getQuantity()) {
                        return ResponseEntity.badRequest().body(Map.of("message", "Không đủ tồn kho khả dụng để reserve"));
                    }
                    p.setStock(stock - req.getQuantity());
                    p.setReservedStock(reserved + req.getQuantity());
                    Product saved = productRepository.save(p);
                    persistIdempotency("reserve", req);
                    return ResponseEntity.ok(toDto(saved));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping("/sold")
    @Transactional
    public ResponseEntity<?> sold(@Valid @RequestBody InventoryStockMutationRequest req) {
        return productRepository.findById(req.getProductId())
                .map(p -> {
                    ResponseEntity<?> replay = handleIdempotencyReplayOrConflict("sold", req, p);
                    if (replay != null) {
                        return replay;
                    }
                    int reserved = p.getReservedStock() != null ? p.getReservedStock() : 0;
                    if (reserved < req.getQuantity()) {
                        return ResponseEntity.badRequest().body(Map.of("message", "Không đủ tồn kho reserve để sold"));
                    }
                    p.setReservedStock(reserved - req.getQuantity());
                    Product saved = productRepository.save(p);
                    persistIdempotency("sold", req);
                    return ResponseEntity.ok(toDto(saved));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}

