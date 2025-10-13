package com.bni.orange.transaction.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransferReadRepository extends JpaRepository<TransferReadRepository, UUID> {
}
