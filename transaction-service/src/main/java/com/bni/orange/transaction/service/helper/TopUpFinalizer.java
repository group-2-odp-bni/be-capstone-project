package com.bni.orange.transaction.service.helper;

import com.bni.orange.transaction.event.TopUpEventPublisher;
import com.bni.orange.transaction.model.entity.Transaction;
import com.bni.orange.transaction.model.entity.TransactionLedger;
import com.bni.orange.transaction.model.entity.VirtualAccount;
import com.bni.orange.transaction.repository.TransactionLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopUpFinalizer {

    private final TransactionLedgerRepository ledgerRepository;
    private final TopUpEventPublisher eventPublisher;

    public void finalizeSuccess(Transaction transaction, VirtualAccount virtualAccount, BigDecimal balanceBefore) {
        createLedgerEntry(transaction, virtualAccount, balanceBefore);
        eventPublisher.publishTopUpCompleted(transaction, virtualAccount);
    }

    public void finalizeFailure(Transaction transaction, VirtualAccount virtualAccount, String errorMessage) {
        eventPublisher.publishTopUpFailed(transaction, virtualAccount, errorMessage);
    }

    private void createLedgerEntry(Transaction transaction, VirtualAccount virtualAccount, BigDecimal balanceBefore) {
        var ledgerEntry = TransactionLedger.createCreditEntry(
            transaction.getId(),
            transaction.getTransactionRef(),
            virtualAccount.getWalletId(),
            virtualAccount.getUserId(),
            virtualAccount.getAmount(),
            balanceBefore,
            "Top-up via " + virtualAccount.getProvider().getDisplayName()
        );
        ledgerEntry.setPerformedByUserId(virtualAccount.getUserId());
        ledgerRepository.save(ledgerEntry);
        log.info("Created CREDIT ledger entry for transactionRef: {}", transaction.getTransactionRef());
    }
}
