package com.bni.orange.transaction.repository;

import com.bni.orange.transaction.model.enums.TxStatus;
import com.bni.orange.transaction.model.enums.TxType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface TransferOltpRepository extends JpaRepository<TransferOltpRepository, UUID>{
}
