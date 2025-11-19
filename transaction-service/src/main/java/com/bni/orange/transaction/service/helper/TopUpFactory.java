package com.bni.orange.transaction.service.helper;

import com.bni.orange.transaction.model.entity.TopUpConfig;
import com.bni.orange.transaction.model.entity.Transaction;
import com.bni.orange.transaction.model.entity.VirtualAccount;
import com.bni.orange.transaction.model.enums.TransactionStatus;
import com.bni.orange.transaction.model.enums.TransactionType;
import com.bni.orange.transaction.model.enums.VirtualAccountStatus;
import com.bni.orange.transaction.model.request.TopUpInitiateRequest;
import com.bni.orange.transaction.model.response.UserProfileResponse;
import com.bni.orange.transaction.repository.TransactionRepository;
import com.bni.orange.transaction.repository.VirtualAccountRepository;
import com.bni.orange.transaction.service.VirtualAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TopUpFactory {

    private final TransactionRepository transactionRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final VirtualAccountService virtualAccountService;

    public Transaction createPendingTopUpTransaction(
        TopUpInitiateRequest request,
        UUID userId,
        TopUpConfig config,
        String transactionRef,
        BigDecimal fee,
        BigDecimal totalAmount,
        UserProfileResponse userProfile
    ) {
        var transaction = Transaction.builder()
            .transactionRef(transactionRef)
            .idempotencyKey(UUID.randomUUID().toString())
            .type(TransactionType.TOP_UP)
            .status(TransactionStatus.PENDING)
            .amount(request.amount())
            .fee(fee)
            .totalAmount(totalAmount)
            .currency("IDR")
            .userId(userId)
            .userName(userProfile.name())
            .userPhone(userProfile.phoneNumber())
            .walletId(request.walletId())
            .counterpartyUserId(null)
            .counterpartyWalletId(null)
            .counterpartyName(config.getProviderName())
            .counterpartyPhone(null)
            .description("Top-up via " + config.getProviderName())
            .build();
        return transactionRepository.save(transaction);
    }

    public VirtualAccount createVirtualAccount(
        TopUpInitiateRequest request,
        Transaction transaction,
        TopUpConfig config,
        UserProfileResponse userProfile
    ) {
        var vaNumber = virtualAccountService.generateVaNumber(config, transaction.getUserId());
        var expiryTime = virtualAccountService.calculateExpiryTime(config);

        var virtualAccount = VirtualAccount.builder()
            .vaNumber(vaNumber)
            .transactionId(transaction.getId())
            .accountName(userProfile.name())
            .userId(transaction.getUserId())
            .walletId(request.walletId())
            .provider(request.provider())
            .status(VirtualAccountStatus.ACTIVE)
            .amount(request.amount())
            .expiresAt(expiryTime)
            .metadata(Map.of(
                "provider", request.provider().name(),
                "transactionRef", transaction.getTransactionRef())
            )
            .build();
        return virtualAccountRepository.save(virtualAccount);
    }
}
