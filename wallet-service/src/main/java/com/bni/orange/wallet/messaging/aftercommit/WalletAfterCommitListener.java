package com.bni.orange.wallet.messaging.aftercommit;

import com.bni.orange.wallet.messaging.WalletEventPublisher;
import lombok.extern.slf4j.Slf4j;
import com.bni.orange.wallet.domain.DomainEvents.WalletUpdated;
import com.bni.orange.wallet.domain.DomainEvents.WalletCreated;
import com.bni.orange.wallet.domain.DomainEvents.WalletInviteAccepted;
import com.bni.orange.wallet.domain.DomainEvents.WalletInviteLinkGenerated;
import com.bni.orange.wallet.domain.DomainEvents.WalletMemberInvited;
import com.bni.orange.wallet.domain.DomainEvents.WalletMembersCleared;

import com.bni.orange.wallet.proto.WalletCreatedEvent;
import com.bni.orange.wallet.proto.WalletUpdatedEvent;
import com.bni.orange.wallet.proto.WalletMemberInvitedEvent;
import com.bni.orange.wallet.proto.WalletMembersClearedEvent;
import com.bni.orange.wallet.proto.WalletInviteLinkGeneratedEvent;
import com.bni.orange.wallet.proto.WalletInviteAcceptedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletAfterCommitListener {

    private final WalletEventPublisher publisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWalletCreated(WalletCreated e) {

        var payload = WalletCreatedEvent.newBuilder()
                .setWalletId(e.getWalletId().toString())
                .setUserId(e.getUserId().toString())
                .setCurrency(e.getCurrency())
                .setStatus(e.getStatus().name())
                .setType(e.getType().name())
                .setName(e.getName() == null ? "" : e.getName())
                
                .setBalanceSnapshot(e.getBalanceSnapshot().toString())
                .setIsDefaultForUser(e.isDefaultForUser())
                .setCreatedAt(e.getCreatedAt().toString())
                .setUpdatedAt(e.getUpdatedAt().toString())
                
                .build();

        publisher.publishWalletCreated(e.getWalletId().toString(), payload);
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWalletUpdated(WalletUpdated e) {

        var payload = WalletUpdatedEvent.newBuilder()
                .setWalletId(e.getWalletId().toString())
                .setUserId(e.getUserId().toString())
                .setCurrency(e.getCurrency())
                .setStatus(e.getStatus().name())
                .setType(e.getType().name())
                .setName(e.getName() == null ? "" : e.getName())
                .setBalanceSnapshot(e.getBalanceSnapshot().toString())
                .setUpdatedAt(e.getUpdatedAt().toString())
                .build();

        publisher.publishWalletUpdated(e.getWalletId().toString(), payload);
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWalletMemberInvited(WalletMemberInvited e) {
            log.info("DEBUG: AFTER_COMMIT listener triggered. Publishing to Kafka...");           
            var payload = WalletMemberInvitedEvent.newBuilder()
                    .setWalletId(e.getWalletId().toString())
                    .setInviterUserId(e.getInviterUserId().toString())
                    .setInvitedUserId(e.getInvitedUserId().toString())
                    .setRole(e.getRole().name())
                    .setWalletName(e.getWalletName() == null ? "" : e.getWalletName())
                    .build();

            publisher.publishWalletMemberInvited(e.getInvitedUserId().toString(), payload);
            log.info("DEBUG: Message sent to Kafka publisher.");
        }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInviteLinkGenerated(WalletInviteLinkGenerated e) {

        log.info("DEBUG: WalletInviteLinkGenerated AFTER_COMMIT triggered");

        var payload = WalletInviteLinkGeneratedEvent.newBuilder()
                .setWalletId(e.getWalletId().toString())
                .setInviterUserId(e.getInviterUserId().toString())
                .setPhoneE164(e.getPhoneE164())
                .setRole(e.getRole().name())
                .setLink(e.getLink())
                .setCodeMasked(e.getCodeMasked() == null ? "" : e.getCodeMasked())
                .setCodePlain(e.getCodePlain() == null ? "" : e.getCodePlain())
                .setExpiresAt(e.getExpiresAt().toString())
                .setNonce(e.getNonce())
                .build();

        String key = e.getWalletId().toString() + ":" + e.getNonce();

        publisher.publishWalletInviteLinkGenerated(key, payload);

        log.info("DEBUG: WalletInviteLinkGenerated sent to Kafka");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInviteAccepted(WalletInviteAccepted e) {

        log.info("DEBUG: WalletInviteAccepted AFTER_COMMIT triggered");

        var payload = WalletInviteAcceptedEvent.newBuilder()
                .setWalletId(e.getWalletId().toString())
                .setUserId(e.getUserId().toString())
                .setRole(e.getRole().name())
                .setOccurredAt(e.getOccurredAt().toString())
                .build();

        publisher.publishWalletInviteAccepted(e.getWalletId().toString(), payload);

        log.info("DEBUG: WalletInviteAccepted sent to Kafka");
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWalletMembersCleared(WalletMembersCleared e) {

        log.info("DEBUG: WalletMembersCleared AFTER_COMMIT triggered");

        var payload = WalletMembersClearedEvent.newBuilder()
                .setWalletId(e.getWalletId().toString())
                .build();

        publisher.publishWalletMembersCleared(
                e.getWalletId().toString(),
                payload
        );

        log.info("DEBUG: WalletMembersCleared sent to Kafka");
    }
}