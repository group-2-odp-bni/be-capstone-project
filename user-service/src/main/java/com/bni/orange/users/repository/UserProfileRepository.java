package com.bni.orange.users.repository;

import com.bni.orange.users.model.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
