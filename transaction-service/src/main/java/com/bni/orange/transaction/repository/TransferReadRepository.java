package com.bni.orange.transaction.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TransferReadRepository extends JpaRepository<TransferReadRepository, UUID> {
}
