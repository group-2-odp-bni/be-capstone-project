package com.bni.orange.transaction.utils;

import java.util.Random;
import java.util.UUID;

public class TransactionIdGenerator {
    private static final Random RANDOM = new Random();

    public static String generate(UUID walletId) {
        long timestamp = System.currentTimeMillis();
        int randomSuffix = RANDOM.nextInt(900) + 100; // 3-digit random number (100-999)
        return walletId + String.valueOf(timestamp) + randomSuffix;
    }
}
