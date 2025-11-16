package com.bni.orange.wallet.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;

@ConfigurationProperties(prefix = "orange.kafka.topics")
public record KafkaTopicProperties(
    @DefaultValue("wallet.events.created")
    String walletCreated,

    @DefaultValue("wallet.events.updated")
    String walletUpdated,

    @DefaultValue("wallet.events.member-invited")
    String walletMemberInvited,

    @DefaultValue("wallet.events.invite-generated")
    String walletInviteGenerated,

    @DefaultValue("wallet.events.invite-accepted")
    String walletInviteAccepted,

    @DefaultValue("wallet.events.members-cleared")
    String walletMembersCleared
) {
}
