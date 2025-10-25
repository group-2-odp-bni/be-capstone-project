package com.bni.orange.transaction.model.enums;

import lombok.Getter;

@Getter
public enum LedgerEntryType {
    DEBIT("Debit", "Debet", -1),
    CREDIT("Credit", "Kredit", 1);

    private final String displayName;
    private final String displayNameId;
    private final int multiplier;

    LedgerEntryType(String displayName, String displayNameId, int multiplier) {
        this.displayName = displayName;
        this.displayNameId = displayNameId;
        this.multiplier = multiplier;
    }

    public static LedgerEntryType fromTransactionType(TransactionType type) {
        return type.isDebit() ? DEBIT : CREDIT;
    }
}
