package com.bni.orange.transaction.service.helper;

import com.bni.orange.transaction.client.BniVaClient;
import com.bni.orange.transaction.client.WalletServiceClient;
import com.bni.orange.transaction.error.BusinessException;
import com.bni.orange.transaction.error.ErrorCode;
import com.bni.orange.transaction.model.entity.Transaction;
import com.bni.orange.transaction.model.entity.VirtualAccount;
import com.bni.orange.transaction.model.enums.PaymentProvider;
import com.bni.orange.transaction.model.request.internal.BalanceUpdateRequest;
import com.bni.orange.transaction.repository.TransactionRepository;
import com.bni.orange.transaction.util.SecurityContextPropagationExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopUpOrchestrator {

    private final WalletServiceClient walletServiceClient;
    private final BniVaClient bniVaClient;
    private final TransactionRepository transactionRepository;
    private final SecurityContextPropagationExecutor securityContextPropagationExecutor;
    private final TopUpFinalizer topUpFinalizer;

    public void registerVirtualAccount(VirtualAccount virtualAccount, Transaction transaction) {
        try {
            var vaRegistrationFuture = securityContextPropagationExecutor.supplyAsyncWithContext(() -> {
                var vaRequest = new BniVaClient.VaRegistrationRequest(
                    virtualAccount.getVaNumber(),
                    virtualAccount.getAmount(),
                    virtualAccount.getAccountName(),
                    virtualAccount.getExpiresAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                );

                return bniVaClient.registerVirtualAccount(vaRequest);
            });

            var vaResponse = vaRegistrationFuture.join();

            if (!vaResponse.success()) {
                throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_ERROR, "Failed to register VA with provider: " + vaResponse.message());
            }

            log.info("VA registered with provider successfully");
        } catch (Exception e) {
            log.error("Failed to register VA with provider", e);
            transaction.markAsFailed("Failed to register VA with provider: " + e.getMessage());
            transactionRepository.save(transaction);
            throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_ERROR, "Failed to register VA with payment provider");
        }
    }

    public void processWalletCredit(Transaction transaction, VirtualAccount virtualAccount, PaymentProvider provider) {
        try {
            var balanceUpdateRequest = BalanceUpdateRequest.builder()
                .walletId(virtualAccount.getWalletId())
                .delta(virtualAccount.getAmount())
                .referenceId(transaction.getTransactionRef())
                .reason("Top-up via " + provider.getDisplayName())
                .actorUserId(transaction.getUserId())
                .build();

            var balanceUpdateResult = walletServiceClient.updateBalance(balanceUpdateRequest).block();

            if (balanceUpdateResult == null || !"OK".equals(balanceUpdateResult.code())) {
                throw new BusinessException(
                    ErrorCode.WALLET_UPDATE_FAILED,
                    balanceUpdateResult != null ? balanceUpdateResult.message() : "Failed to update wallet balance"
                );
            }

            transaction.markAsSuccess();
            transactionRepository.save(transaction);

            topUpFinalizer.finalizeSuccess(transaction, virtualAccount, balanceUpdateResult.previousBalance());

            log.info("Top-up completed successfully for transaction: {}, new balance: {}",
                transaction.getTransactionRef(), balanceUpdateResult.newBalance());

        } catch (Exception e) {
            log.error("Failed to credit wallet balance", e);
            transaction.markAsFailed("Failed to credit wallet: " + e.getMessage());
            transactionRepository.save(transaction);

            topUpFinalizer.finalizeFailure(transaction, virtualAccount, e.getMessage());

            throw new BusinessException(ErrorCode.WALLET_UPDATE_FAILED, "Failed to credit wallet balance");
        }
    }

    public void cancelVirtualAccount(VirtualAccount virtualAccount) {
        securityContextPropagationExecutor.runAsyncWithContext(() -> {
            try {
                bniVaClient.cancelVirtualAccount(virtualAccount.getVaNumber());
            } catch (Exception e) {
                log.error("Failed to cancel VA with provider", e);
            }
        }).join();
    }
}
