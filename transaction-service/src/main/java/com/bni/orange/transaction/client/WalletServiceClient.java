package com.bni.orange.transaction.client;

import com.bni.orange.transaction.client.base.BaseServiceClient;
import com.bni.orange.transaction.error.BusinessException;
import com.bni.orange.transaction.error.ErrorCode;
import com.bni.orange.transaction.model.enums.WalletPermission;
import com.bni.orange.transaction.model.request.BalanceAdjustmentRequest;
import com.bni.orange.transaction.model.response.ApiResponse;
import com.bni.orange.transaction.model.response.BalanceResponse;
import com.bni.orange.transaction.model.response.WalletAccessValidation;
import com.bni.orange.transaction.model.response.WalletResolutionResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class WalletServiceClient extends BaseServiceClient {

    public WalletServiceClient(
        WebClient walletServiceWebClient,
        Retry externalServiceRetry,
        CircuitBreaker externalServiceCircuitBreaker
    ) {
        super(walletServiceWebClient, externalServiceRetry, externalServiceCircuitBreaker, "wallet-service");
    }

    @Override
    protected ErrorCode getServiceErrorCode() {
        return ErrorCode.WALLET_SERVICE_ERROR;
    }

    public Mono<BalanceResponse> getBalance(UUID walletId) {
        log.debug("Getting balance for wallet: {}", walletId);

        return executeGet(
            uriSpec -> uriSpec.uri("/api/v1/wallets/{walletId}/balance", walletId),
            new ParameterizedTypeReference<>() {},
            notFoundMapper(ErrorCode.WALLET_NOT_FOUND, "Wallet not found: " + walletId)
        );
    }

    public Mono<BalanceResponse> adjustBalance(
        UUID walletId,
        BalanceAdjustmentRequest request,
        String idempotencyKey
    ) {
        log.debug("Adjusting balance for wallet: {}, amount: {}", walletId, request.amount());

        return executePost(
            uriSpec -> uriSpec
                .uri("/api/v1/wallets/{walletId}/balance/adjust", walletId)
                .header("Idempotency-Key", idempotencyKey)
                .bodyValue(request),
            new ParameterizedTypeReference<ApiResponse<BalanceResponse>>() {},
            conflictMapper(ErrorCode.INSUFFICIENT_BALANCE, "Insufficient balance for wallet: " + walletId)
        );
    }

    public Mono<WalletAccessValidation> validateAccess(
        UUID walletId,
        UUID userId,
        WalletPermission permission
    ) {
        log.debug("Validating wallet access: walletId={}, userId={}, permission={}",
            walletId, userId, permission);

        return executeGet(
            uriSpec -> uriSpec.uri(uriBuilder -> uriBuilder
                .path("/internal/wallets/{walletId}/validate")
                .queryParam("userId", userId)
                .queryParam("permission", permission)
                .build(walletId)),
            new ParameterizedTypeReference<ApiResponse<WalletAccessValidation>>() {},
            notFoundMapper(ErrorCode.WALLET_NOT_FOUND, "Wallet not found: " + walletId)
        ).doOnSuccess(validation -> {
            if (!validation.hasAccess()) {
                log.warn("Wallet access denied: walletId={}, userId={}, reason={}",
                    walletId, userId, validation.denialReason());
            } else {
                log.debug("Wallet access granted: walletId={}, userId={}, role={}",
                    walletId, userId, validation.userRole());
            }
        });
    }

    public Mono<WalletResolutionResponse> resolveRecipientWallet(
        String identifier,
        String identifierType,
        String currency
    ) {
        log.debug("Resolving recipient wallet: identifier={}, type={}, currency={}",
            identifier, identifierType, currency);

        var requestBody = Map.of(
            "identifier", identifier,
            "identifierType", identifierType != null ? identifierType : "PHONE",
            "currency", currency != null ? currency : "IDR"
        );

        return executePost(
            uriSpec -> uriSpec
                .uri("/internal/wallets/_resolve-recipient")
                .bodyValue(requestBody),
            new ParameterizedTypeReference<ApiResponse<WalletResolutionResponse>>() {},
            notFoundMapper(ErrorCode.RECIPIENT_WALLET_NOT_FOUND, "Recipient has no default wallet set for receiving transfers")
        ).doOnSuccess(wallet -> log.debug("Resolved recipient wallet: walletId={}, name={}, type={}",
            wallet.walletId(), wallet.walletName(), wallet.walletType()));
    }

    public Mono<List<UUID>> getUserWalletIds(UUID userId) {
        log.debug("Getting wallet IDs for user: {}", userId);

        return executeGet(
            uriSpec -> uriSpec.uri(uriBuilder -> uriBuilder
                .path("/internal/users/{userId}/wallets")
                .queryParam("idsOnly", true)
                .build(userId)),
            new ParameterizedTypeReference<ApiResponse<List<UUID>>>() {}
        ).doOnSuccess(walletIds -> log.debug("User {} has access to {} wallets", userId, walletIds.size()));
    }
}
