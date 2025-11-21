package com.bni.orange.wallet.service.command;

import com.bni.orange.wallet.model.request.wallet.WalletCreateRequest;
import com.bni.orange.wallet.model.request.wallet.WalletUpdateRequest;
import com.bni.orange.wallet.model.response.WalletDetailResponse;
import com.bni.orange.wallet.model.response.wallet.WalletDeleteResultResponse;

import java.util.UUID;

public interface WalletCommandService {
  WalletDetailResponse createWallet(WalletCreateRequest req, String idempotencyKey);
  WalletDetailResponse createWalletForUser(UUID userId, WalletCreateRequest req, String idempotencyKey);
  WalletDetailResponse updateWallet(UUID walletId, WalletUpdateRequest req);
  WalletDeleteResultResponse deleteWallet(UUID walletId);
  WalletDeleteResultResponse confirmDeleteWallet(String token);
  WalletDeleteResultResponse approveDeleteWallet(String token);

}
