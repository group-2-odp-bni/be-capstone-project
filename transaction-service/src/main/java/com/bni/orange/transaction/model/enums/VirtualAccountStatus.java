package com.bni.orange.transaction.model.enums;

public enum VirtualAccountStatus {
    ACTIVE,
    PAID,
    EXPIRED,
    CANCELLED;

    public boolean canBePaid() {
        return this == ACTIVE;
    }

    public boolean canBeCancelled() {
        return this == ACTIVE;
    }

    public boolean isFinal() {
        return this == PAID || this == EXPIRED || this == CANCELLED;
    }
}
