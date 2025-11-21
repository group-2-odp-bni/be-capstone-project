package com.bni.orange.transaction.client;

import com.bni.orange.transaction.client.base.BaseServiceClient;
import com.bni.orange.transaction.error.ErrorCode;
import com.bni.orange.transaction.model.request.internal.BalanceUpdateRequest;
import com.bni.orange.transaction.model.request.internal.BalanceValidateRequest;
import com.bni.orange.transaction.model.request.internal.RoleValidateRequest;
import com.bni.orange.transaction.model.request.internal.ValidateWalletOwnershipRequest;
import com.bni.orange.transaction.model.response.WalletResolutionResponse;
import com.bni.orange.transaction.model.response.internal.BalanceUpdateResponse;
import com.bni.orange.transaction.model.response.internal.InternalApiResponse;
import com.bni.orange.transaction.model.response.internal.RoleValidateResponse;
import com.bni.orange.transaction.model.response.internal.UserWalletsResponse;
import com.bni.orange.transaction.model.response.internal.ValidateWalletOwnershipResponse;
import com.bni.orange.transaction.model.response.internal.ValidationResultResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
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

    public Mono<ValidationResultResponse> validateBalance(BalanceValidateRequest request) {
        log.debug("Validating balance for wallet: {}, amount: {}, action: {}",
            request.walletId(), request.amount(), request.action());

        return executePostInternal(
            uriSpec -> uriSpec
                .uri("/internal/v1/wallets/balance:validate")
                .bodyValue(request),
            new ParameterizedTypeReference<InternalApiResponse<ValidationResultResponse>>() {},
            notFoundMapper(ErrorCode.WALLET_NOT_FOUND, "Wallet not found: " + request.walletId())
        ).doOnSuccess(result -> {
            if (!result.allowed()) {
                log.warn("Balance validation failed: walletId={}, code={}, message={}",
                    request.walletId(), result.code(), result.message());
            } else {
                log.debug("Balance validation passed: walletId={}", request.walletId());
            }
        });
    }

    public Mono<BalanceUpdateResponse> updateBalance(BalanceUpdateRequest request) {
        log.debug("Updating balance for wallet: {}, delta: {}, referenceId: {}",
            request.walletId(), request.delta(), request.referenceId());

        return executePostInternal(
            uriSpec -> uriSpec
                .uri("/internal/v1/wallets/balance:update")
                .bodyValue(request),
            new ParameterizedTypeReference<InternalApiResponse<BalanceUpdateResponse>>() {},
            conflictMapper(ErrorCode.INSUFFICIENT_BALANCE, "Insufficient balance for wallet: " + request.walletId())
        ).doOnSuccess(result -> {
            if ("OK".equals(result.code())) {
                log.info("Balance updated successfully: walletId={}, previous={}, new={}, delta={}",
                    result.walletId(), result.previousBalance(), result.newBalance(), request.delta());
            } else {
                log.warn("Balance update failed: walletId={}, code={}, message={}",
                    result.walletId(), result.code(), result.message());
            }
        });
    }

    public Mono<RoleValidateResponse> validateRole(RoleValidateRequest request) {
        log.debug("Validating role for wallet: {}, userId={}, action={}",
            request.walletId(), request.userId(), request.action());

        return executePostInternal(
            uriSpec -> uriSpec
                .uri("/internal/v1/wallets/roles:validate")
                .bodyValue(request),
            new ParameterizedTypeReference<InternalApiResponse<RoleValidateResponse>>() {},
            notFoundMapper(ErrorCode.WALLET_NOT_FOUND, "Wallet not found: " + request.walletId())
        ).doOnSuccess(result -> {
            if (!result.allowed()) {
                log.warn("Role validation failed: walletId={}, userId={}, code={}, message={}",
                    request.walletId(), request.userId(), result.code(), result.message());
            } else {
                log.debug("Role validation passed: walletId={}, userId={}, role={}",
                    request.walletId(), request.userId(), result.effectiveRole());
            }
        });
    }

    public Mono<WalletResolutionResponse> getDefaultWalletByUserId(UUID userId) {
        log.debug("Getting default wallet for user: {}", userId);

        return executeGetInternal(
            uriSpec -> uriSpec.uri("/internal/v1/users/{userId}/default-wallet", userId),
            new ParameterizedTypeReference<InternalApiResponse<WalletResolutionResponse>>() {},
            notFoundMapper(ErrorCode.WALLET_NOT_FOUND, "User not found or has no default wallet: " + userId)
        ).doOnSuccess(wallet -> {
            if (wallet.walletId() != null) {
                log.debug("Found default wallet for user {}: walletId={}, name={}, type={}",
                    userId, wallet.walletId(), wallet.walletName(), wallet.walletType());
            } else {
                log.warn("User {} has no default wallet set", userId);
            }
        });
    }

    public Mono<List<UUID>> getUserWalletIds(UUID userId) {
        log.debug("Getting wallet IDs for user: {}", userId);

        return executeGetInternal(
            uriSpec -> uriSpec.uri(uriBuilder -> uriBuilder
                .path("/internal/v1/users/{userId}/wallets")
                .queryParam("idsOnly", true)
                .build(userId)),
            new ParameterizedTypeReference<InternalApiResponse<UserWalletsResponse>>() {
            }
        ).map(UserWalletsResponse::walletIds)
            .doOnSuccess(walletIds -> log.debug("User {} has access to {} wallets", userId, walletIds.size()));
    }

    public Mono<ValidateWalletOwnershipResponse> validateWalletOwnership(ValidateWalletOwnershipRequest request) {
        log.debug("Validating wallet ownership for user: {} on wallets: {}", request.userId(), request.walletIds());

        return executePostInternal(
            uriSpec -> uriSpec
                .uri("/internal/v1/wallets/ownership:validate")
                .bodyValue(request),
            new ParameterizedTypeReference<InternalApiResponse<ValidateWalletOwnershipResponse>>() {}
        ).doOnSuccess(result -> {
            if (result.isOwner()) {
                log.debug("Wallet ownership validation passed for user: {}", request.userId());
            } else {
                log.warn("Wallet ownership validation failed for user: {}", request.userId());
            }
        });
    }
}
