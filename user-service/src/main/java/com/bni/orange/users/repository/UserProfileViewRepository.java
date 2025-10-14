package com.bni.orange.users.repository;

import com.bni.orange.users.model.entity.UserProfileView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for querying the user profile read model (materialized view).
 * This repository is part of the Query Side in CQRS architecture.
 * All methods here are read-only operations.
 */
@Repository
public interface UserProfileViewRepository extends JpaRepository<UserProfileView, UUID> {

    /**
     * Find user profile by phone number.
     *
     * @param phoneNumber the phone number to search for
     * @return Optional containing the user profile if found
     */
    Optional<UserProfileView> findByPhoneNumber(String phoneNumber);

    /**
     * Check if a user exists by phone number.
     *
     * @param phoneNumber the phone number to check
     * @return true if user exists, false otherwise
     */
    boolean existsByPhoneNumber(String phoneNumber);
}
