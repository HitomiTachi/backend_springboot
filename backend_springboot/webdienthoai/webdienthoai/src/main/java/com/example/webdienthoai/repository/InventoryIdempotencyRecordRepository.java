package com.example.webdienthoai.repository;

import com.example.webdienthoai.entity.InventoryIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryIdempotencyRecordRepository extends JpaRepository<InventoryIdempotencyRecord, Long> {
    Optional<InventoryIdempotencyRecord> findByIdempotencyKey(String idempotencyKey);
}

