package com.bni.orange.transaction.service;

import com.bni.orange.transaction.client.WalletServiceClient;
import com.bni.orange.transaction.error.BusinessException;
import com.bni.orange.transaction.error.ErrorCode;
import com.bni.orange.transaction.model.enums.TransactionStatus;
import com.bni.orange.transaction.model.response.TransactionResponse;
import com.bni.orange.transaction.repository.TransactionRepository;
import com.bni.orange.transaction.repository.specification.TransactionSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionHistoryService {

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final WalletServiceClient walletServiceClient;

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getUserTransactions(
        UUID userId,
        UUID walletId,
        TransactionStatus status,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        Pageable pageable
    ) {
        log.debug("Getting transactions for user: {}, walletId: {}", userId, walletId);

        if (walletId != null) {
            var userWallets = walletServiceClient.getUserWalletIds(userId).block();
            if (userWallets == null || !userWallets.contains(walletId)) {
                log.warn("User {} attempted to access unauthorized wallet {}", userId, walletId);
                throw new BusinessException(ErrorCode.WALLET_ACCESS_DENIED, "You don't have access to this wallet");
            }
        }

        return transactionRepository
            .findAll(TransactionSpecification.buildSpecification(userId, walletId, status, startDate, endDate), pageable)
            .map(transactionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getAllWalletTransactions(
        UUID userId,
        TransactionStatus status,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        Pageable pageable
    ) {
        log.debug("Getting transactions across all wallets for user: {}", userId);

        var walletIds = walletServiceClient.getUserWalletIds(userId).block();

        if (Objects.isNull(walletIds) || walletIds.isEmpty()) {
            log.warn("User {} has no accessible wallets", userId);
            return Page.empty(pageable);
        }

        log.debug("User {} has access to {} wallets", userId, walletIds.size());

        return transactionRepository.findAll(
            TransactionSpecification.buildSpecificationForUserWallets(walletIds, status, startDate, endDate),
            pageable
        ).map(transactionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionDetail(UUID transactionId, UUID userId) {
        var transaction = transactionRepository
            .findById(transactionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found: " + transactionId));

        if (!transaction.belongsToUser(userId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found or you don't have permission");
        }

        return transactionMapper.toResponse(transaction);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionByRef(String transactionRef, UUID userId) {
        var transaction = transactionRepository
            .findByTransactionRefAndUserId(transactionRef, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found: " + transactionRef));

        return transactionMapper.toResponse(transaction);
    }
}
