package com.bni.orange.transaction.service;

import com.bni.orange.transaction.model.entity.Transaction;
import com.bni.orange.transaction.model.response.TransactionResponse;
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
}
