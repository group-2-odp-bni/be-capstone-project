package com.bni.orange.wallet.service;

import com.bni.orange.wallet.exception.InsufficientFundsException;
import com.bni.orange.wallet.exception.WalletStatusConflictException;
import com.bni.orange.wallet.model.entity.Wallet;
import com.bni.orange.wallet.model.enums.WalletStatus;
import com.bni.orange.wallet.model.request.*;
import com.bni.orange.wallet.model.response.*;
import com.bni.orange.wallet.repository.AuthUserLookupRepository;
import com.bni.orange.wallet.repository.WalletRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository repo;
    private final AuthUserLookupRepository authRead;
    private final WalletEventsPublisher events; // bisa null

    public WalletService(WalletRepository repo,
                         AuthUserLookupRepository authRead,
                         ObjectProvider<WalletEventsPublisher> eventsProvider) {
        this.repo = repo;
        this.authRead = authRead;
        this.events = eventsProvider.getIfAvailable();
    }

    @Transactional
    public WalletResponse createWallet(WalletCreateRequest req,
                                       String idempotencyKey,
                                       String xRequestId,
                                       String xCorrelationId) {
        var currency = (req.currency()==null || req.currency().isBlank()) ? "IDR" : req.currency().toUpperCase();
        var existing = repo.findByUserIdAndCurrency(req.user_id(), currency).orElse(null);
        if (existing != null) return toResponse(existing);

        try {
            var w = new Wallet();
            w.setUserId(req.user_id());
            w.setCurrency(currency);
            w.setStatus(WalletStatus.ACTIVE);
            var saved = repo.saveAndFlush(w);

            if (events != null) events.walletCreated(saved, xRequestId, xCorrelationId);

            return toResponse(saved);
        } catch (DataIntegrityViolationException dup) {
            var raced = repo.findByUserIdAndCurrency(req.user_id(), currency).orElseThrow(() -> dup);
            return toResponse(raced);
        }
    }

    @Transactional(readOnly = true)
    public WalletResponse getById(UUID walletId) {
        var w = repo.findById(walletId).orElseThrow(() -> new NoSuchElementException("wallet not found"));
        return toResponse(w);
    }

    @Transactional(readOnly = true)
    public WalletResponse getByPhone(String phone, String currency) {
        var user = authRead.findByPhoneNumber(phone)
                .orElseThrow(() -> new NoSuchElementException("user not found for phone"));
        var normalized = (currency == null || currency.isBlank()) ? "IDR" : currency.toUpperCase();
        var w = repo.findByUserIdAndCurrency(user.getId(),normalized)
                .orElseThrow(() -> new NoSuchElementException("wallet not found"));
        return toResponse(w);
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(UUID walletId) {
        var w = repo.findById(walletId).orElseThrow(() -> new NoSuchElementException("wallet not found"));
        return new BalanceResponse(w.getId(), w.getCurrency(), w.getBalanceSnapshot(), OffsetDateTime.now());
    }

    @Transactional
    public BalanceResponse adjustBalance(UUID walletId,
                                         BalanceAdjustRequest req,
                                         String idempotencyKey,
                                         String xRequestId,
                                         String xCorrelationId) {
        if (req.amount().compareTo(BigDecimal.ZERO) == 0)
            throw new IllegalArgumentException("amount must not be zero");

        var w = repo.lockById(walletId).orElseThrow(() -> new NoSuchElementException("wallet not found"));
        if (w.getStatus() != WalletStatus.ACTIVE) throw new WalletStatusConflictException("WALLET_STATUS_NOT_ACTIVE");

        var newBalance = w.getBalanceSnapshot().add(req.amount());
        if (newBalance.compareTo(BigDecimal.ZERO) < 0)
            throw new InsufficientFundsException("NEGATIVE_BALANCE_NOT_ALLOWED");

        w.setBalanceSnapshot(newBalance);
        var saved = repo.save(w);

        if (events != null) events.balanceAdjusted(saved, req.amount(), req.reason(), xRequestId, xCorrelationId);

        return new BalanceResponse(saved.getId(), saved.getCurrency(), saved.getBalanceSnapshot(), OffsetDateTime.now());
    }

    private WalletResponse toResponse(Wallet w){
        return new WalletResponse(w.getId(), w.getUserId(), w.getCurrency(), w.getStatus(),
                w.getBalanceSnapshot(), w.getCreatedAt(), w.getUpdatedAt());
    }
}
