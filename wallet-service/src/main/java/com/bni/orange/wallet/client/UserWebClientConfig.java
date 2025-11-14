package com.bni.orange.wallet.client;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

@Configuration
public class UserWebClientConfig {

    @Value("${app.internal-user.base-url}")
    private String userBaseUrl;

    @Bean(name = "internalUserWebClient")
    public WebClient internalUserWebClient() {

        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(5, TimeUnit.SECONDS))
                .addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.SECONDS))
            );

        return WebClient.builder()
            .baseUrl(userBaseUrl)
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
}
