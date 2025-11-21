package com.bni.orange.notification.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

  @Bean
  public NewTopic otpWhatsapp() {
    return TopicBuilder.name("notification.otp.whatsapp")
        .partitions(3)           
        .replicas(1)             
        .build();
  }

  @Bean
  public NewTopic walletMemberInvited() {
    return TopicBuilder.name("wallet.events.member-invited")
        .partitions(3)
        .replicas(1)
        .build();
  }
  @Bean
  public NewTopic walletInviteGenerated() {
    return TopicBuilder.name("wallet.events.invite-generated")
        .partitions(3)
        .replicas(1)
        .build();
  }

  @Bean
  public NewTopic walletInviteAccepted() {
    return TopicBuilder.name("wallet.events.invite-accepted")
        .partitions(3)
        .replicas(1)
        .build();
  }
}
