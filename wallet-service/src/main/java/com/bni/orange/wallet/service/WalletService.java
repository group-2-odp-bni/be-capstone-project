package com.bni.orange.wallet.service;

import com.bni.orange.wallet.model.entity.Wallet;
import com.bni.orange.wallet.model.enums.WalletStatus;
import com.bni.orange.wallet.model.response.BalanceResponse;
import com.bni.orange.wallet.model.response.WalletResponse;
import com.bni.orange.wallet.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository repo;

    public WalletService(WalletRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public WalletResponse createWallet(UUID userId, String currency) {
        // idempotent by userId+currency
        var existing = repo.findByUserIdAndCurrency(userId, currency).orElse(null);
        if (existing != null) return toResponse(existing);

        var w = new Wallet();
        w.setUserId(userId);
        w.setCurrency(currency);
        w.setStatus(WalletStatus.ACTIVE);
        var saved = repo.save(w);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public WalletResponse getById(UUID walletId) {
        var w = repo.findById(walletId).orElseThrow(() ->
                new NoSuchElementException("wallet not found"));
        return toResponse(w);
    }

    @Transactional(readOnly = true)
    public WalletResponse getByUser(UUID userId, String currency) {
        var w = repo.findByUserIdAndCurrency(userId, currency).orElseThrow(() ->
                new NoSuchElementException("wallet not found"));
        return toResponse(w);
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(UUID walletId) {
        var w = repo.findById(walletId).orElseThrow(() ->
                new NoSuchElementException("wallet not found"));
        return new BalanceResponse(
                w.getId(), w.getCurrency(), w.getBalanceSnapshot(), OffsetDateTime.now()
        );
    }

    private WalletResponse toResponse(Wallet w) {
        return new WalletResponse(
                w.getId(), w.getUserId(), w.getCurrency(), w.getStatus(),
                w.getBalanceSnapshot(), w.getCreatedAt(), w.getUpdatedAt()
        );
    }
}
