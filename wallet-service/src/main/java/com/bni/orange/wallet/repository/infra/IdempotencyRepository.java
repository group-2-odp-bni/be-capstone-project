package com.bni.orange.wallet.repository.infra;

import com.bni.orange.wallet.model.entity.infra.Idempotency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyRepository extends JpaRepository<Idempotency, Long> {
  Optional<Idempotency> findByScopeAndIdemKey(String scope, String idemKey);
}
