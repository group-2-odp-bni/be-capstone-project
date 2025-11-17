package com.bni.orange.transaction.service.helper;

import com.bni.orange.transaction.client.WalletServiceClient;
import com.bni.orange.transaction.error.BusinessException;
import com.bni.orange.transaction.error.ErrorCode;
import com.bni.orange.transaction.model.entity.Transaction;
import com.bni.orange.transaction.model.enums.TransactionType;
import com.bni.orange.transaction.model.enums.TransferType;
import com.bni.orange.transaction.model.request.internal.BalanceUpdateRequest;
import com.bni.orange.transaction.model.response.BalanceResponse;
import com.bni.orange.transaction.model.response.TransactionResponse;
import com.bni.orange.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferOrchestrator {

    private final WalletServiceClient walletServiceClient;
    private final TransactionRepository transactionRepository;
    private final TransferFinalizer transferFinalizer;

    public TransactionResponse executeTransfer(Transaction transaction) {
        log.info("Executing transfer: {}", transaction.getTransactionRef());
        BalanceResponse senderBalance;

        try {
            senderBalance = debitSender(transaction);
        } catch (Exception e) {
            transferFinalizer.handleTransferFailure(transaction, e);
            throw e;
        }

        try {
            var receiverBalance = creditReceiver(transaction);
            return transferFinalizer.finalizeSuccessfulTransfer(transaction, senderBalance, receiverBalance);
        } catch (Exception e) {
            log.error("Failed to credit receiver, reversing sender debit", e);
            reverseSenderDebit(transaction);
            transferFinalizer.handleTransferFailure(transaction, e);
            throw e;
        }
    }

    public TransactionResponse executeInternalTransferSaga(Transaction transaction) {
        log.info("Executing internal transfer saga: {}", transaction.getTransactionRef());
        BalanceResponse senderBalance;
        BalanceResponse receiverBalance;
        var senderDebited = false;
        var receiverCredited = false;

        try {
            senderBalance = debitSender(transaction);
            senderDebited = true;
            log.info("Step 1/3: Sender debited successfully for internal transfer: {}", transaction.getTransactionRef());

            receiverBalance = creditReceiver(transaction);
            receiverCredited = true;
            log.info("Step 2/3: Receiver credited successfully for internal transfer: {}", transaction.getTransactionRef());

            var result = transferFinalizer.finalizeSuccessfulInternalTransfer(transaction, senderBalance, receiverBalance);
            log.info("Step 3/3: Internal transfer finalized successfully: {}", transaction.getTransactionRef());
            return result;

        } catch (Exception e) {
            log.error("Internal transfer saga failed at some step, initiating rollback: {}", transaction.getTransactionRef(), e);

            if (receiverCredited) {
                log.warn("Rolling back receiver credit for transaction: {}", transaction.getTransactionRef());
                reverseReceiverCredit(transaction);
            }

            if (senderDebited) {
                log.warn("Rolling back sender debit for transaction: {}", transaction.getTransactionRef());
                reverseSenderDebit(transaction);
            }

            transferFinalizer.handleTransferFailure(transaction, e);
            throw e;
        }
    }

    private BalanceResponse debitSender(Transaction transaction) {
        var transferType = transaction.getType().isTransfer() &&
            (transaction.getType() == TransactionType.INTERNAL_TRANSFER_OUT ||
             transaction.getType() == TransactionType.INTERNAL_TRANSFER_IN)
            ? TransferType.INTERNAL
            : TransferType.EXTERNAL;

        var balanceUpdateRequest = BalanceUpdateRequest.builder()
            .walletId(transaction.getWalletId())
            .delta(transaction.getTotalAmount().negate())
            .referenceId(transaction.getIdempotencyKey() + "-sender")
            .reason("Transfer to " + transaction.getCounterpartyName())
            .actorUserId(transaction.getUserId())
            .transferType(transferType)
            .build();

        var balanceUpdateResult = Optional.ofNullable(walletServiceClient.updateBalance(balanceUpdateRequest).block())
            .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_SERVICE_ERROR, "Failed to debit sender"));

        if (!"OK".equals(balanceUpdateResult.code())) {
            throw new BusinessException(ErrorCode.WALLET_SERVICE_ERROR, balanceUpdateResult.message());
        }

        log.debug("Sender debited successfully. New balance: {}", balanceUpdateResult.newBalance());

        return BalanceResponse.builder()
            .balance(balanceUpdateResult.newBalance())
            .balanceBefore(balanceUpdateResult.previousBalance())
            .balanceAfter(balanceUpdateResult.newBalance())
            .currency("IDR")
            .build();
    }

    private BalanceResponse creditReceiver(Transaction transaction) {
        var transferType = transaction.getType().isTransfer() &&
            (transaction.getType() == TransactionType.INTERNAL_TRANSFER_OUT ||
             transaction.getType() == TransactionType.INTERNAL_TRANSFER_IN)
            ? TransferType.INTERNAL
            : TransferType.EXTERNAL;

        var balanceUpdateRequest = BalanceUpdateRequest.builder()
            .walletId(transaction.getCounterpartyWalletId())
            .delta(transaction.getAmount())
            .referenceId(transaction.getIdempotencyKey() + "-receiver")
            .reason("Transfer from sender")
            .actorUserId(transaction.getUserId())
            .transferType(transferType)
            .build();

        var balanceUpdateResult = Optional.ofNullable(walletServiceClient.updateBalance(balanceUpdateRequest).block())
            .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_SERVICE_ERROR, "Failed to credit receiver"));

        if (!"OK".equals(balanceUpdateResult.code())) {
            throw new BusinessException(ErrorCode.WALLET_SERVICE_ERROR, balanceUpdateResult.message());
        }

        log.debug("Receiver credited successfully. New balance: {}", balanceUpdateResult.newBalance());

        return BalanceResponse.builder()
            .balance(balanceUpdateResult.newBalance())
            .balanceBefore(balanceUpdateResult.previousBalance())
            .balanceAfter(balanceUpdateResult.newBalance())
            .currency("IDR")
            .build();
    }

    private void reverseSenderDebit(Transaction transaction) {
        log.warn("Reversing sender debit for transaction: {}", transaction.getTransactionRef());

        var transferType = transaction.getType().isTransfer() &&
            (transaction.getType() == TransactionType.INTERNAL_TRANSFER_OUT ||
             transaction.getType() == TransactionType.INTERNAL_TRANSFER_IN)
            ? TransferType.INTERNAL
            : TransferType.EXTERNAL;

        var balanceUpdateRequest = BalanceUpdateRequest.builder()
            .walletId(transaction.getWalletId())
            .delta(transaction.getTotalAmount())
            .referenceId(transaction.getIdempotencyKey() + "-sender-reversal")
            .reason("Reversal for failed transfer: " + transaction.getTransactionRef())
            .actorUserId(transaction.getUserId())
            .transferType(transferType)
            .build();

        try {
            var result = walletServiceClient.updateBalance(balanceUpdateRequest).block();
            if (result != null && "OK".equals(result.code())) {
                log.info("Sender debit reversed successfully. New balance: {}", result.newBalance());
            } else {
                log.error("CRITICAL: Reversal returned non-OK status: {}", result != null ? result.message() : "null");
            }
        } catch (Exception e) {
            log.error("CRITICAL: Failed to reverse sender debit. Manual intervention required.", e);
        }
    }

    private void reverseReceiverCredit(Transaction transaction) {
        log.warn("Reversing receiver credit for transaction: {}", transaction.getTransactionRef());

        var transferType = transaction.getType().isTransfer() &&
            (transaction.getType() == TransactionType.INTERNAL_TRANSFER_OUT ||
             transaction.getType() == TransactionType.INTERNAL_TRANSFER_IN)
            ? TransferType.INTERNAL
            : TransferType.EXTERNAL;

        var balanceUpdateRequest = BalanceUpdateRequest.builder()
            .walletId(transaction.getCounterpartyWalletId())
            .delta(transaction.getAmount().negate())
            .referenceId(transaction.getIdempotencyKey() + "-receiver-reversal")
            .reason("Reversal for failed transfer: " + transaction.getTransactionRef())
            .actorUserId(transaction.getUserId())
            .transferType(transferType)
            .build();

        try {
            var result = walletServiceClient.updateBalance(balanceUpdateRequest).block();
            if (result != null && "OK".equals(result.code())) {
                log.info("Receiver credit reversed successfully. New balance: {}", result.newBalance());
            } else {
                log.error("CRITICAL: Receiver reversal returned non-OK status: {}", result != null ? result.message() : "null");
            }
        } catch (Exception e) {
            log.error("CRITICAL: Failed to reverse receiver credit. Manual intervention required.", e);
        }
    }
}
