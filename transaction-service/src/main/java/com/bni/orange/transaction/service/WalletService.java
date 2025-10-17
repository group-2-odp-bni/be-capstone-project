package com.bni.orange.transaction.service;

import com.bni.orange.transaction.model.response.CheckBalanceResponse;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WebClient webClient;

    public Mono<String> checkBalance(String walletId) {
        return webClient.get()
                .uri("/wallets/{walletId}/balance", walletId)
                .retrieve()
                .bodyToMono(String.class);
    }
}
