package com.bni.orange.transaction.client;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class BniVaClient {

    public VaRegistrationResponse registerVirtualAccount(VaRegistrationRequest request) {
        log.info("Mock: Registering VA {} with BNI for amount {}", maskVaNumber(request.vaNumber()), request.amount());

        return VaRegistrationResponse.builder()
            .success(true)
            .statusCode("SUCCESS")
            .message("VA registered successfully")
            .vaNumber(request.vaNumber())
            .build();
    }

    public VaCancellationResponse cancelVirtualAccount(String vaNumber) {
        log.info("Mock: Cancelling VA {} with BNI", maskVaNumber(vaNumber));

        return VaCancellationResponse.builder()
            .success(true)
            .statusCode("SUCCESS")
            .message("VA cancelled successfully")
            .build();
    }

    public VaStatusResponse checkVirtualAccountStatus(String vaNumber) {
        log.info("Mock: Checking VA status {} with BNI", maskVaNumber(vaNumber));

        return VaStatusResponse.builder()
            .success(true)
            .status("ACTIVE")
            .message("VA is active")
            .build();
    }

    private String maskVaNumber(String vaNumber) {
        if (vaNumber == null || vaNumber.length() <= 8) {
            return "****";
        }
        return vaNumber.substring(0, 4) + "********" + vaNumber.substring(vaNumber.length() - 4);
    }

    @Builder
    public record VaRegistrationRequest(
        String vaNumber,
        BigDecimal amount,
        String customerName,
        String expiryDateTime
    ) {
    }

    @Builder
    public record VaRegistrationResponse(
        boolean success,
        String statusCode,
        String message,
        String vaNumber
    ) {
    }

    @Builder
    public record VaCancellationResponse(
        boolean success,
        String statusCode,
        String message
    ) {
    }

    @Builder
    public record VaStatusResponse(
        boolean success,
        String status,
        String message
    ) {
    }
}
