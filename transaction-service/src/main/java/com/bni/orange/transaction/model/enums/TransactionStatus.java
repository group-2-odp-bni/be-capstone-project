package com.bni.orange.transaction.model.enums;

import lombok.Getter;

@Getter
public enum TransactionStatus {
    PENDING("Pending", "Menunggu", false, false),
    PROCESSING("Processing", "Diproses", false, false),
    SUCCESS("Success", "Berhasil", true, false),
    FAILED("Failed", "Gagal", true, true),
    REVERSED("Reversed", "Dibatalkan", true, true);

    private final String displayName;
    private final String displayNameId;
    private final boolean isFinal;
    private final boolean isError;

    TransactionStatus(String displayName, String displayNameId, boolean isFinal, boolean isError) {
        this.displayName = displayName;
        this.displayNameId = displayNameId;
        this.isFinal = isFinal;
        this.isError = isError;
    }

    public boolean isInProgress() {
        return this == PENDING || this == PROCESSING;
    }

    public boolean isSuccessful() {
        return this == SUCCESS;
    }

    public boolean canBeProcessed() {
        return this == PENDING;
    }

    public boolean requiresReversal() {
        return this == PROCESSING; // Only PROCESSING state can be reversed
    }
}
