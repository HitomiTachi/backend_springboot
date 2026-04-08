package com.example.webdienthoai.repository;

import com.example.webdienthoai.entity.AdminInboxRead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AdminInboxReadRepository extends JpaRepository<AdminInboxRead, Long> {
    List<AdminInboxRead> findByAdminUserIdAndAuditIdIn(Long adminUserId, Collection<Long> auditIds);
    Optional<AdminInboxRead> findByAdminUserIdAndAuditId(Long adminUserId, Long auditId);
}
