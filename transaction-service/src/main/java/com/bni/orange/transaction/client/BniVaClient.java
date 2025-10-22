package com.bni.orange.transaction.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Mock BNI Virtual Account API Client
 * TODO: Replace with actual BNI VA API integration
 */
@Slf4j
@Component
public class BniVaClient {

    /**
     * Register VA with BNI provider
     * Mock implementation - always returns success
     */
    public VaRegistrationResponse registerVirtualAccount(VaRegistrationRequest request) {
        log.info("Mock: Registering VA {} with BNI for amount {}",
            maskVaNumber(request.vaNumber()), request.amount());

        // Mock successful registration
        return new VaRegistrationResponse(
            true,
            "SUCCESS",
            "VA registered successfully",
            request.vaNumber()
        );
    }

    /**
     * Cancel VA with BNI provider
     * Mock implementation - always returns success
     */
    public VaCancellationResponse cancelVirtualAccount(String vaNumber) {
        log.info("Mock: Cancelling VA {} with BNI", maskVaNumber(vaNumber));

        // Mock successful cancellation
        return new VaCancellationResponse(
            true,
            "SUCCESS",
            "VA cancelled successfully"
        );
    }

    /**
     * Check VA status with BNI provider
     * Mock implementation - always returns active
     */
    public VaStatusResponse checkVirtualAccountStatus(String vaNumber) {
        log.info("Mock: Checking VA status {} with BNI", maskVaNumber(vaNumber));

        // Mock active status
        return new VaStatusResponse(
            true,
            "ACTIVE",
            "VA is active"
        );
    }

    private String maskVaNumber(String vaNumber) {
        if (vaNumber == null || vaNumber.length() <= 8) {
            return "****";
        }
        return vaNumber.substring(0, 4) + "********" + vaNumber.substring(vaNumber.length() - 4);
    }

    /**
     * VA Registration Request
     */
    public record VaRegistrationRequest(
        String vaNumber,
        BigDecimal amount,
        String customerName,
        String expiryDateTime
    ) {
    }

    /**
     * VA Registration Response
     */
    public record VaRegistrationResponse(
        boolean success,
        String statusCode,
        String message,
        String vaNumber
    ) {
    }

    /**
     * VA Cancellation Response
     */
    public record VaCancellationResponse(
        boolean success,
        String statusCode,
        String message
    ) {
    }

    /**
     * VA Status Response
     */
    public record VaStatusResponse(
        boolean success,
        String status,
        String message
    ) {
    }
}
