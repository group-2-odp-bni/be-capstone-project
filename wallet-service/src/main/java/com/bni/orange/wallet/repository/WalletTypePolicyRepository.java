package com.bni.orange.wallet.repository;

import com.bni.orange.wallet.model.entity.WalletTypePolicy;
import com.bni.orange.wallet.model.enums.WalletType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTypePolicyRepository extends JpaRepository<WalletTypePolicy, WalletType> { }
