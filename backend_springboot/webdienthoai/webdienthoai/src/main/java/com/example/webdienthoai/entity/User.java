package com.example.webdienthoai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    private String phone;
    private String gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "avatar_url", length = 1000)
    private String avatarUrl;

    @Column(nullable = false)
    @Builder.Default
    private String role = "customer";

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    /** Null = chưa xác minh email (chỉ tài khoản mới sau khi bật tính năng). */
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "auth_provider", length = 20)
    @Builder.Default
    private String authProvider = "LOCAL";

    @Column(name = "provider_id", length = 191)
    private String providerId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Order> orders = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
