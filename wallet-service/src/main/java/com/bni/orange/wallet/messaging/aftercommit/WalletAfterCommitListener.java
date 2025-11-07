package com.bni.orange.wallet.messaging.aftercommit;

import com.bni.orange.wallet.messaging.WalletEventPublisher;

import com.bni.orange.wallet.domain.DomainEvents.WalletUpdated;
import com.bni.orange.wallet.domain.DomainEvents.WalletCreated;
import com.bni.orange.wallet.domain.DomainEvents.WalletMemberInvited;
import com.bni.orange.wallet.proto.WalletCreatedEvent;
import com.bni.orange.wallet.proto.WalletUpdatedEvent;
import com.bni.orange.wallet.proto.WalletMemberInvitedEvent;
import com.bni.orange.wallet.service.command.projector.WalletReadModelProjector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
@RequiredArgsConstructor
public class WalletAfterCommitListener {

    private final WalletEventPublisher publisher;
    private final WalletReadModelProjector readModelProjector;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWalletCreated(WalletCreated e) {
        try {
            readModelProjector.projectNewWallet(e);
        } catch (Exception ex) {
            // Log but don't throw - we don't want to break Kafka publishing
            // Read model can be rebuilt later if needed
            System.err.println("ERROR: Failed to project wallet to read model: " + e.getWalletId());
            ex.printStackTrace();
        }

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
        // Synchronously update read model
        readModelProjector.projectWalletUpdateFromEvent(e);

        // Publish to Kafka for external consumers
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
            
            var payload = WalletMemberInvitedEvent.newBuilder()
                    .setWalletId(e.getWalletId().toString())
                    .setInviterUserId(e.getInviterUserId().toString())
                    .setInvitedUserId(e.getInvitedUserId().toString())
                    .setRole(e.getRole().name())
                    .setWalletName(e.getWalletName() == null ? "" : e.getWalletName())
                    .build();

            publisher.publishWalletMemberInvited(e.getInvitedUserId().toString(), payload);
        }
}