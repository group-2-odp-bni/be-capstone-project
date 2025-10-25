package com.bni.orange.transaction.model.enums;

import lombok.Getter;

@Getter
public enum TransactionType {
    TRANSFER_OUT("Transfer Out", "Transfer keluar"),
    TRANSFER_IN("Transfer In", "Transfer masuk"),
    TOP_UP("Top Up", "Isi saldo"),
    PAYMENT("Payment", "Pembayaran"),
    REFUND("Refund", "Pengembalian dana"),
    WITHDRAWAL("Withdrawal", "Penarikan");

    private final String displayName;
    private final String displayNameId;

    TransactionType(String displayName, String displayNameId) {
        this.displayName = displayName;
        this.displayNameId = displayNameId;
    }

    public boolean isTransfer() {
        return this == TRANSFER_OUT || this == TRANSFER_IN;
    }

    public boolean isDebit() {
        return this == TRANSFER_OUT || this == PAYMENT || this == WITHDRAWAL;
    }

    public boolean isCredit() {
        return this == TRANSFER_IN || this == TOP_UP || this == REFUND;
    }
}
