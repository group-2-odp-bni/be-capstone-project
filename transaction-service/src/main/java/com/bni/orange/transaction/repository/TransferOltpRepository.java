package com.bni.orange.transaction.repository;

import com.bni.orange.transaction.model.entity.TransferOltpEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferOltpRepository extends JpaRepository<TransferOltpEntity, UUID> {

}
