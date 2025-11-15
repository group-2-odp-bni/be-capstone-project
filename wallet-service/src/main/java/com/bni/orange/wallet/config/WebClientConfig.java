package com.bni.orange.wallet.config;

import com.bni.orange.wallet.config.properties.InternalUserProperties;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({
    InternalUserProperties.class
})
public class WebClientConfig {

    private final InternalUserProperties userServiceConfig;

    private HttpClient createHttpClientWithTimeout(
        Duration connectTimeout,
        Duration readTimeout,
        Duration writeTimeout,
        int maxConnections,
        int pendingAcquireTimeout,
        String poolName
    ) {
        var connectionProvider = ConnectionProvider.builder(poolName)
            .maxConnections(maxConnections)
            .pendingAcquireTimeout(Duration.ofSeconds(pendingAcquireTimeout))
            .build();

        return HttpClient.create(connectionProvider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
            .responseTimeout(readTimeout)
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(readTimeout.toSeconds(), TimeUnit.SECONDS))
                .addHandlerLast(new WriteTimeoutHandler(writeTimeout.toSeconds(), TimeUnit.SECONDS))
            );
    }

    @Bean
    public WebClient userWebClient() {
        var httpClient = createHttpClientWithTimeout(
            Duration.ofMillis(userServiceConfig.getConnectTimeout()),
            Duration.ofSeconds(userServiceConfig.getReadTimeout()),
            Duration.ofSeconds(userServiceConfig.getWriteTimeout()),
            userServiceConfig.getMaxConnections(),
            userServiceConfig.getPendingAcquireTimeout(),
            "user-service-pool"
        );

        return WebClient.builder()
            .baseUrl(userServiceConfig.getBaseUrl())
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
}
