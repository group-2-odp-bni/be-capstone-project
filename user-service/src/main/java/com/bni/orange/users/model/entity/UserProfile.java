package com.bni.orange.users.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@Table(
    name = "user_profiles",
    schema = "user_oltp",
    indexes = {
        @Index(name = "idx_user_profiles_email", columnList = "email"),
        @Index(name = "idx_user_profiles_phone", columnList = "phone_number"),
        @Index(name = "idx_user_profiles_pending_email", columnList = "pending_email"),
        @Index(name = "idx_user_profiles_pending_phone", columnList = "pending_phone")
    }
)
public class UserProfile extends BaseEntity {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String phoneNumber;

    @Column(unique = true)
    private String email;

    private String pendingEmail;

    @Column(length = 50)
    private String pendingPhone;

    private OffsetDateTime emailVerifiedAt;

    private OffsetDateTime phoneVerifiedAt;

    private LocalDate dateOfBirth;

    @Column(columnDefinition = "TEXT")
    private String profileImageUrl;

    @Transient
    public boolean hasVerifiedEmail() {
        return email != null && emailVerifiedAt != null;
    }

    @Transient
    public boolean hasVerifiedPhone() {
        return phoneNumber != null && phoneVerifiedAt != null;
    }

    @Transient
    public boolean hasPendingEmail() {
        return pendingEmail != null;
    }

    @Transient
    public boolean hasPendingPhone() {
        return pendingPhone != null;
    }

    public void applyVerifiedEmail(String verifiedEmail) {
        this.email = verifiedEmail;
        this.pendingEmail = null;
        this.emailVerifiedAt = OffsetDateTime.now();
    }

    public void applyVerifiedPhone(String verifiedPhone) {
        this.phoneNumber = verifiedPhone;
        this.pendingPhone = null;
        this.phoneVerifiedAt = OffsetDateTime.now();
    }
}
