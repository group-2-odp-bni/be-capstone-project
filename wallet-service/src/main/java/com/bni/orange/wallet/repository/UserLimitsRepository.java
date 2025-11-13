package com.bni.orange.wallet.repository;

import com.bni.orange.wallet.model.entity.UserLimits;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserLimitsRepository extends JpaRepository<UserLimits, UUID> {
  Optional<UserLimits> findByUserId(UUID userId);
  boolean existsByUserId(UUID userId); 
}
