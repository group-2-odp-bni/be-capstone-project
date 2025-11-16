package com.bni.orange.transaction.service.helper;

import com.bni.orange.transaction.model.entity.Transaction;
import com.bni.orange.transaction.model.enums.TransactionStatus;
import com.bni.orange.transaction.model.enums.TransactionType;
import com.bni.orange.transaction.model.request.InternalTransferRequest;
import com.bni.orange.transaction.model.request.TransferInitiateRequest;
import com.bni.orange.transaction.model.response.UserProfileResponse;
import com.bni.orange.transaction.repository.TransactionRepository;
import com.bni.orange.transaction.utils.TransactionRefGenerator;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionFactory {

    private final TransactionRepository transactionRepository;
    private final TransactionRefGenerator refGenerator;

    @Getter
    @Value("${orange.transaction.fee.transfer:0}")
    private BigDecimal transferFee;

    public Transaction createPendingTransfer(
        TransferInitiateRequest request,
        UUID senderUserId,
        String idempotencyKey,
        UserProfileResponse senderInfo,
        UserProfileResponse receiverInfo
    ) {
        var transactionRef = refGenerator.generate();

        var senderTransaction = Transaction.builder()
            .transactionRef(transactionRef)
            .idempotencyKey(idempotencyKey)
            .type(TransactionType.TRANSFER_OUT)
            .status(TransactionStatus.PENDING)
            .amount(request.amount())
            .fee(transferFee)
            .currency(request.currency())
            .userId(senderUserId)
            .userName(senderInfo.name())
            .userPhone(senderInfo.phoneNumber())
            .walletId(request.senderWalletId())
            .counterpartyUserId(request.receiverUserId())
            .counterpartyWalletId(request.receiverWalletId())
            .counterpartyName(receiverInfo.name())
            .counterpartyPhone(receiverInfo.phoneNumber())
            .notes(request.notes())
            .description("Transfer to " + receiverInfo.name())
            .splitBillId(request.splitBillId())
            .splitBillMemberId(request.splitBillMemberId())
            .build();

        senderTransaction.calculateTotalAmount();

        return transactionRepository.save(senderTransaction);
    }

    public Transaction createAndSavePendingInternalTransaction(
        InternalTransferRequest request,
        UUID userId,
        String idempotencyKey,
        String sourceWalletName,
        String destinationWalletName
    ) {
        var transactionRef = refGenerator.generate();

        var transaction = Transaction.builder()
            .transactionRef(transactionRef)
            .idempotencyKey(idempotencyKey)
            .type(TransactionType.INTERNAL_TRANSFER_OUT)
            .status(TransactionStatus.PENDING)
            .amount(request.amount())
            .fee(BigDecimal.ZERO)
            .currency("IDR")
            .userId(userId)
            .userName(sourceWalletName)
            .userPhone(null)
            .walletId(request.sourceWalletId())
            .counterpartyUserId(userId)
            .counterpartyWalletId(request.destinationWalletId())
            .counterpartyName(destinationWalletName)
            .counterpartyPhone(null)
            .notes("Internal Transfer")
            .description("Transfer from " + sourceWalletName + " to " + destinationWalletName)
            .build();

        transaction.calculateTotalAmount();

        return transactionRepository.save(transaction);
    }

    public Transaction createReceiverTransaction(Transaction senderTransaction, TransactionType type) {
        return Transaction.builder()
            .transactionRef(senderTransaction.getTransactionRef())
            .idempotencyKey(senderTransaction.getIdempotencyKey() + "-receiver")
            .type(type)
            .status(TransactionStatus.PROCESSING)
            .amount(senderTransaction.getAmount())
            .fee(BigDecimal.ZERO)
            .totalAmount(senderTransaction.getAmount())
            .currency(senderTransaction.getCurrency())
            .userId(senderTransaction.getCounterpartyUserId())
            .userName(senderTransaction.getCounterpartyName())
            .userPhone(senderTransaction.getCounterpartyPhone())
            .walletId(senderTransaction.getCounterpartyWalletId())
            .counterpartyUserId(senderTransaction.getUserId())
            .counterpartyWalletId(senderTransaction.getWalletId())
            .counterpartyName(senderTransaction.getUserName())
            .counterpartyPhone(senderTransaction.getUserPhone())
            .notes(senderTransaction.getNotes())
            .description("Transfer from " + senderTransaction.getUserName())
            .splitBillId(senderTransaction.getSplitBillId())
            .splitBillMemberId(senderTransaction.getSplitBillMemberId())
            .build();
    }
}
