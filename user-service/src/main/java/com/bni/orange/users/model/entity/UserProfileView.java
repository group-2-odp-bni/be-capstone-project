package com.bni.orange.users.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.util.UUID;


@Getter
@Setter
@Entity
@Immutable
@NoArgsConstructor
@Table(name = "user_profile_read_model", schema = "auth_oltp")
public class UserProfileView {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String phoneNumber;

    @Column(unique = true)
    private String email;

    private String profileImageUrl;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Boolean phoneVerified;

    @Column(nullable = false)
    private Boolean emailVerified;

    private LocalDateTime lastLoginAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
