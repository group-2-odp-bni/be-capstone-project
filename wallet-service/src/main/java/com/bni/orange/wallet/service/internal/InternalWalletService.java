package com.bni.orange.wallet.service.internal;

import com.bni.orange.wallet.model.request.internal.BalanceUpdateRequest;
import com.bni.orange.wallet.model.request.internal.BalanceValidateRequest;
import com.bni.orange.wallet.model.request.internal.RoleValidateRequest;
import com.bni.orange.wallet.model.response.internal.BalanceUpdateResponse;
import com.bni.orange.wallet.model.response.internal.DefaultWalletResponse;
import com.bni.orange.wallet.model.response.internal.RoleValidateResponse;
import com.bni.orange.wallet.model.response.internal.ValidationResultResponse;

import java.util.UUID;

public interface InternalWalletService {
  ValidationResultResponse validateBalance(BalanceValidateRequest req);
  BalanceUpdateResponse updateBalance(BalanceUpdateRequest req);
  RoleValidateResponse validateRole(RoleValidateRequest req);
  DefaultWalletResponse getDefaultWalletByUserId(UUID userId);
}
