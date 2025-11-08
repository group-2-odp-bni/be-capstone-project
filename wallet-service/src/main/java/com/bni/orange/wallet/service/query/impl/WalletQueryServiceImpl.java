package com.bni.orange.wallet.service.query.impl;

import com.bni.orange.wallet.exception.business.ResourceNotFoundException;
import com.bni.orange.wallet.model.entity.read.UserWalletRead;
import com.bni.orange.wallet.model.entity.read.WalletRead;
import com.bni.orange.wallet.model.mapper.WalletMapper;
import com.bni.orange.wallet.model.response.BalanceResponse;
import com.bni.orange.wallet.model.response.WalletDetailResponse;
import com.bni.orange.wallet.model.response.WalletListItemResponse;
import com.bni.orange.wallet.repository.WalletRepository;
import com.bni.orange.wallet.repository.read.UserWalletReadRepository;
import com.bni.orange.wallet.repository.read.WalletReadRepository;
import com.bni.orange.wallet.service.query.WalletQueryService;
import com.bni.orange.wallet.utils.metadata.MetadataFilter;
import com.bni.orange.wallet.utils.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class WalletQueryServiceImpl implements WalletQueryService {

  private final WalletReadRepository walletReadRepo;
  private final UserWalletReadRepository userWalletReadRepo;
  private final WalletRepository walletRepo;
  private final WalletMapper mapper;
  private final ObjectMapper om;

  public WalletQueryServiceImpl(
      WalletReadRepository walletReadRepo,
      UserWalletReadRepository userWalletReadRepo,
      WalletRepository walletRepo,
      WalletMapper mapper,
      ObjectMapper om) {
    this.walletReadRepo = walletReadRepo;
    this.userWalletReadRepo = userWalletReadRepo;
    this.walletRepo = walletRepo;
    this.mapper = mapper;
    this.om = om;
  }

  @Override
  public List<WalletListItemResponse> listMyWallets(int page, int size) {
    var uid = CurrentUser.userId();
    var p = userWalletReadRepo.findByUserId(uid, PageRequest.of(page, size));
    var walletIds = p.map(UserWalletRead::getWalletId).toList();
    if (walletIds.isEmpty()) return List.of();

    var reads = walletReadRepo.findAllById(walletIds);
    return reads.stream()
        .sorted(Comparator.comparing(WalletRead::getUpdatedAt).reversed())
        .map(mapper::toListItem)
        .toList();
  }

  @Override
  public WalletDetailResponse getWalletDetail(UUID walletId) {
    var read = walletReadRepo.findById(walletId)
        .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
    var oltp = walletRepo.findById(walletId)
        .orElseThrow(() -> new ResourceNotFoundException("Wallet (oltp) not found"));
    var filtered = MetadataFilter.filter(oltp.getMetadata());
    return mapper.mergeDetail(read, oltp, filtered);
  }

  @Override
  public BalanceResponse getBalance(UUID walletId) {
    var read = walletReadRepo.findById(walletId)
        .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
    return BalanceResponse.builder()
        .walletId(walletId)
        .balance(read.getBalanceSnapshot())
        .currency(read.getCurrency())
        .build();
  }
}
