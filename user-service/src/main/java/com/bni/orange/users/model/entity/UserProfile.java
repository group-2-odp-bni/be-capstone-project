package com.bni.orange.users.model.entity;

import com.bni.orange.users.model.enums.SyncStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
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

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(columnDefinition = "TEXT")
    private String address;

    private OffsetDateTime syncedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status")
    private SyncStatus syncStatus;

    // ============================================
    // Transient Methods - Compatibility with Auth Service
    // ============================================

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

    // ============================================
    // Sync Methods - From Authentication Service Events
    // ============================================

    /**
     * Sync core user data from authentication-service.
     * Called when UserRegistered or UserUpdated event is received.
     *
     * @param name from auth service
     * @param phoneNumber from auth service
     * @param email from auth service (can be null)
     * @param profileImageUrl from auth service (can be null)
     * @param phoneVerified from auth service
     * @param emailVerified from auth service
     */
    public void syncFromAuthService(
        String name,
        String phoneNumber,
        String email,
        String profileImageUrl,
        Boolean phoneVerified,
        Boolean emailVerified
    ) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.profileImageUrl = profileImageUrl;

        // Convert Boolean to OffsetDateTime
        if (phoneVerified != null && phoneVerified && this.phoneVerifiedAt == null) {
            this.phoneVerifiedAt = OffsetDateTime.now();
        } else if (phoneVerified != null && !phoneVerified) {
            this.phoneVerifiedAt = null;
        }

        if (emailVerified != null && emailVerified && this.emailVerifiedAt == null) {
            this.emailVerifiedAt = OffsetDateTime.now();
        } else if (emailVerified != null && !emailVerified) {
            this.emailVerifiedAt = null;
        }

        this.syncedAt = OffsetDateTime.now();
        this.syncStatus = SyncStatus.SYNCED;
    }

    /**
     * Apply verified email after OTP verification in user-service.
     * This updates the pending email to actual email.
     */
    public void applyVerifiedEmail(String verifiedEmail) {
        this.email = verifiedEmail;
        this.pendingEmail = null;
        this.emailVerifiedAt = OffsetDateTime.now();
    }

    /**
     * Apply verified phone after OTP verification in user-service.
     * This updates the pending phone to actual phone.
     */
    public void applyVerifiedPhone(String verifiedPhone) {
        this.phoneNumber = verifiedPhone;
        this.pendingPhone = null;
        this.phoneVerifiedAt = OffsetDateTime.now();
    }

    /**
     * Mark this profile as pending sync when sync fails.
     */
    public void markSyncFailed() {
        this.syncStatus = SyncStatus.SYNC_FAILED;
    }

    /**
     * Mark this profile as pending sync when waiting for auth service event.
     */
    public void markPendingSync() {
        this.syncStatus = SyncStatus.PENDING_SYNC;
    }
}
