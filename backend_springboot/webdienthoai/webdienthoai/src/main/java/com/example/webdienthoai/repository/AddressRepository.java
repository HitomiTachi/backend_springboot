package com.example.webdienthoai.repository;

import com.example.webdienthoai.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserIdOrderByIdDesc(Long userId);

    long countByUserId(Long userId);

    java.util.Optional<Address> findFirstByUserIdAndIsDefaultTrue(Long userId);
}
