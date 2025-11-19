package com.bni.orange.users.repository;

import com.bni.orange.users.model.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByPhoneNumber(String phoneNumber);

    Optional<UserProfile> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END " +
           "FROM UserProfile u WHERE u.email = :email AND u.id != :userId")
    boolean existsByEmailAndIdNot(String email, UUID userId);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END " +
           "FROM UserProfile u WHERE u.phoneNumber = :phoneNumber AND u.id != :userId")
    boolean existsByPhoneNumberAndIdNot(String phoneNumber, UUID userId);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END " +
           "FROM UserProfile u WHERE (u.email = :email OR u.pendingEmail = :email) AND u.id != :userId")
    boolean isPendingEmailAlreadyInUse(String email, UUID userId);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END " +
           "FROM UserProfile u WHERE (u.phoneNumber = :phone OR u.pendingPhone = :phone) AND u.id != :userId")
    boolean isPendingPhoneAlreadyInUse(String phone, UUID userId);

    /**
     * Atomically apply verified email only if pendingEmail matches the expected value.
     * This prevents race condition where another thread might have updated pendingEmail
     * between verification and save.
     *
     * @param userId User ID
     * @param verifiedEmail The verified email to set as actual email
     * @return Number of rows updated (1 if success, 0 if race condition occurred)
     */
    @Modifying
    @Query("UPDATE UserProfile u " +
           "SET u.email = :verifiedEmail, " +
           "u.pendingEmail = NULL, " +
           "u.emailVerifiedAt = CURRENT_TIMESTAMP, " +
           "u.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE u.id = :userId " +
           "AND u.pendingEmail = :verifiedEmail")
    int applyVerifiedEmailConditionally(UUID userId, String verifiedEmail);

    /**
     * Atomically apply verified phone only if pendingPhone matches the expected value.
     * This prevents race condition where another thread might have updated pendingPhone
     * between verification and save.
     *
     * @param userId User ID
     * @param verifiedPhone The verified phone to set as actual phone
     * @return Number of rows updated (1 if success, 0 if race condition occurred)
     */
    @Modifying
    @Query("UPDATE UserProfile u " +
           "SET u.phoneNumber = :verifiedPhone, " +
           "u.pendingPhone = NULL, " +
           "u.phoneVerifiedAt = CURRENT_TIMESTAMP, " +
           "u.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE u.id = :userId " +
           "AND u.pendingPhone = :verifiedPhone")
    int applyVerifiedPhoneConditionally(UUID userId, String verifiedPhone);
}
