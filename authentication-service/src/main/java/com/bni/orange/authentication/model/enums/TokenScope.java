package com.bni.orange.authentication.model.enums;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum TokenScope {
    PIN_SETUP("PIN_SETUP"),
    PIN_LOGIN("PIN_LOGIN"),
    PIN_RESET("PIN_RESET"),
    FULL_ACCESS("FULL_ACCESS");

    private final String value;

    TokenScope(String value) {
        this.value = value;
    }

    public static TokenScope fromString(String value) {
        return Arrays.stream(values())
            .filter(scope -> scope.value.equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown token scope: " + value));
    }

    @Override
    public String toString() {
        return value;
    }
}
