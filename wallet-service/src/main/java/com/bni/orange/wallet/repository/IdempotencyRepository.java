package com.bni.orange.wallet.repository;

import com.bni.orange.wallet.model.entity.Idempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IdempotencyRepository extends JpaRepository<Idempotency, Long> {
    Optional<Idempotency> findByScopeAndIdemKey(String scope, String idemKey);
}
