package com.bni.orange.wallet.service.query;

import com.bni.orange.wallet.model.response.BalanceResponse;
import com.bni.orange.wallet.model.response.WalletDetailResponse;
import com.bni.orange.wallet.model.response.WalletListItemResponse;

import java.util.List;
import java.util.UUID;

public interface WalletQueryService {
  List<WalletListItemResponse> listMyWallets(int page, int size);
  WalletDetailResponse getWalletDetail(UUID walletId);
  BalanceResponse getBalance(UUID walletId);
}