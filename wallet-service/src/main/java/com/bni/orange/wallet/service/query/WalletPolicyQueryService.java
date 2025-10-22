package com.bni.orange.wallet.service.query;

import com.bni.orange.wallet.model.enums.WalletType;
import com.bni.orange.wallet.model.response.policy.WalletPolicyResponse;

import java.util.UUID;

public interface WalletPolicyQueryService {

  WalletPolicyResponse getWalletPolicy(UUID walletId);

  WalletPolicyResponse getPolicyByType(WalletType type);
}
