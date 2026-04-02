package com.example.webdienthoai.repository;

import com.example.webdienthoai.entity.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {
    List<ReturnRequest> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    List<ReturnRequest> findByStatusOrderByCreatedAtDesc(String status);
}

