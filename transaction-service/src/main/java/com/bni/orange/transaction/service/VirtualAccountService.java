package com.bni.orange.transaction.service;

import com.bni.orange.transaction.model.entity.TopUpConfig;
import com.bni.orange.transaction.model.entity.VirtualAccount;
import com.bni.orange.transaction.repository.VirtualAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualAccountService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int VA_NUMBER_LENGTH = 16;
    private final VirtualAccountRepository virtualAccountRepository;

    public String generateVaNumber(TopUpConfig config, UUID userId) {
        String prefix = config.getVaPrefix();
        int remainingDigits = VA_NUMBER_LENGTH - prefix.length();

        int maxAttempts = 10;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String vaNumber = prefix + generateRandomDigits(remainingDigits);

            if (!virtualAccountRepository.existsByVaNumber(vaNumber)) {
                log.info("Generated unique VA number: {} for userId: {}", maskVaNumber(vaNumber), userId);
                return vaNumber;
            }

            log.warn("VA number collision detected, retrying... (attempt {}/{})", attempt + 1, maxAttempts);
        }

        throw new IllegalStateException("Failed to generate unique VA number after " + maxAttempts + " attempts");
    }

    public OffsetDateTime calculateExpiryTime(TopUpConfig config) {
        return OffsetDateTime.now().plusHours(config.getVaExpiryHours());
    }

    public VirtualAccount findByVaNumberWithLock(String vaNumber) {
        return this.virtualAccountRepository
            .findByVaNumberWithLock(vaNumber)
            .orElseThrow(() -> new IllegalArgumentException("Virtual Account not found: " + maskVaNumber(vaNumber)));
    }

    public VirtualAccount findByTransactionId(UUID transactionId) {
        return this.virtualAccountRepository
            .findByTransactionId(transactionId)
            .orElseThrow(() -> new IllegalArgumentException("Virtual Account not found for transaction: " + transactionId));
    }

    public VirtualAccount save(VirtualAccount virtualAccount) {
        return virtualAccountRepository.save(virtualAccount);
    }

    private String generateRandomDigits(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private String maskVaNumber(String vaNumber) {
        if (vaNumber == null || vaNumber.length() <= 8) {
            return "****";
        }
        return vaNumber.substring(0, 4) + "********" + vaNumber.substring(vaNumber.length() - 4);
    }
}
