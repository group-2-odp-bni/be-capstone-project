package com.bni.orange.wallet.repository;

import com.bni.orange.wallet.model.entity.AuthUserLookup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthUserLookupRepository extends JpaRepository<AuthUserLookup, UUID> {
    Optional<AuthUserLookup> findByPhoneNumber(String phoneNumber);
}
