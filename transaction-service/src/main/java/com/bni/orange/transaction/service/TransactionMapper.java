package com.bni.orange.transaction.service;

import com.bni.orange.transaction.model.entity.Transaction;
import com.bni.orange.transaction.model.response.TransactionResponse;
import com.bni.orange.transaction.model.response.TransactionSummaryResponse;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction transaction) {
        if (transaction == null) {
            return null;
        }

        return TransactionResponse.builder()
            .id(transaction.getId())
            .transactionRef(transaction.getTransactionRef())
            .type(transaction.getType())
            .status(transaction.getStatus())
            .amount(transaction.getAmount())
            .fee(transaction.getFee())
            .totalAmount(transaction.getTotalAmount())
            .currency(transaction.getCurrency())
            .userId(transaction.getUserId())
            .userName(transaction.getUserName())
            .userPhone(transaction.getUserPhone())
            .walletId(transaction.getWalletId())
            .counterpartyUserId(transaction.getCounterpartyUserId())
            .counterpartyWalletId(transaction.getCounterpartyWalletId())
            .counterpartyName(transaction.getCounterpartyName())
            .counterpartyPhone(transaction.getCounterpartyPhone())
            .description(transaction.getDescription())
            .notes(transaction.getNotes())
            .metadata(transaction.getMetadata())
            .completedAt(transaction.getCompletedAt())
            .failedAt(transaction.getFailedAt())
            .failureReason(transaction.getFailureReason())
            .createdAt(transaction.getCreatedAt())
            .updatedAt(transaction.getUpdatedAt())
            .build();
    }

    public TransactionSummaryResponse toSummaryResponse(Transaction transaction) {
        if (transaction == null) {
            return null;
        }

        return TransactionSummaryResponse.builder()
            .id(transaction.getId())
            .transactionRef(transaction.getTransactionRef())
            .type(transaction.getType())
            .status(transaction.getStatus())
            .amount(transaction.getAmount())
            .currency(transaction.getCurrency())
            .displayName(resolveDisplayName(transaction))
            .displaySubtitle(resolveDisplaySubtitle(transaction))
            .createdAt(transaction.getCreatedAt())
            .build();
    }

    private String resolveDisplayName(Transaction transaction) {
        return switch (transaction.getType()) {
            case TRANSFER_OUT, TRANSFER_IN, PAYMENT ->
                transaction.getCounterpartyName() != null ? transaction.getCounterpartyName() : "Unknown";
            case INTERNAL_TRANSFER_OUT, INTERNAL_TRANSFER_IN ->
                transaction.getUserName() != null ? transaction.getUserName() : "Unknown";
            case TOP_UP ->
                transaction.getCounterpartyName() != null ? transaction.getCounterpartyName() : "Top Up";
            case REFUND -> "Refund";
            case WITHDRAWAL -> "Withdrawal";
        };
    }

    private String resolveDisplaySubtitle(Transaction transaction) {
        return switch (transaction.getType()) {
            case TRANSFER_OUT, TRANSFER_IN ->
                transaction.getCounterpartyPhone() != null ? transaction.getCounterpartyPhone() : "Transfer";
            case INTERNAL_TRANSFER_OUT ->
                "To " + transaction.getCounterpartyName();
            case INTERNAL_TRANSFER_IN ->
                "From " + transaction.getCounterpartyName();
            case TOP_UP ->
                "Top Up";
            case PAYMENT ->
                transaction.getDescription() != null ? transaction.getDescription() : "Payment";
            case REFUND ->
                transaction.getDescription() != null ? transaction.getDescription() : "Refund";
            case WITHDRAWAL ->
                transaction.getDescription() != null ? transaction.getDescription() : "Withdrawal";
        };
    }
}
