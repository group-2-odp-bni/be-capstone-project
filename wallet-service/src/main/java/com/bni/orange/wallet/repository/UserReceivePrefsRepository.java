package com.bni.orange.wallet.repository;

import com.bni.orange.wallet.model.entity.UserReceivePrefs;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserReceivePrefsRepository extends JpaRepository<UserReceivePrefs, UUID> {
  Optional<UserReceivePrefs> findByUserId(UUID userId);
}
