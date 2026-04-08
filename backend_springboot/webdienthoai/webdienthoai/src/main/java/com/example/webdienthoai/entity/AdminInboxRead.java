package com.example.webdienthoai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "admin_inbox_reads",
        uniqueConstraints = @UniqueConstraint(name = "uk_admin_inbox_reads_user_audit", columnNames = {"admin_user_id", "audit_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminInboxRead {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Column(name = "audit_id", nullable = false)
    private Long auditId;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;
}
