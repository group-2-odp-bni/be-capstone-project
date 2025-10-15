package com.bni.orange.wallet.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class WalletKafkaConfig {

    @Bean
    public NewTopic walletCreatedV1() {
        return TopicBuilder.name("wallet.created.v1")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic balanceAdjustedV1() {
        return TopicBuilder.name("wallet.balance.adjusted.v1")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
