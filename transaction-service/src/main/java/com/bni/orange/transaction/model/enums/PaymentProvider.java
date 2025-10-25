package com.bni.orange.transaction.model.enums;

public enum PaymentProvider {
    BNI_VA("BNI Virtual Account", "7152"),
    MANDIRI_VA("Mandiri Virtual Account", "8877"),
    BCA_VA("BCA Virtual Account", "7130"),
    PERMATA_VA("Permata Virtual Account", "8528");

    private final String displayName;
    private final String vaPrefix;

    PaymentProvider(String displayName, String vaPrefix) {
        this.displayName = displayName;
        this.vaPrefix = vaPrefix;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getVaPrefix() {
        return vaPrefix;
    }
}
