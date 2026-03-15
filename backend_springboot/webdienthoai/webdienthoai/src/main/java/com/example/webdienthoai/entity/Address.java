package com.example.webdienthoai.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    private String phone;
    private String street;
    private String apartment;
    private String city;
    private String state;
    private String zipCode;
    private String country;

    /** Home, Office, Other */
    private String label;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isDefault = false;
}
