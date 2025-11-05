package com.bni.orange.wallet.messaging.aftercommit;

import com.bni.orange.wallet.messaging.WalletEventPublisher;

import com.bni.orange.wallet.domain.DomainEvents.WalletUpdated;
import com.bni.orange.wallet.domain.DomainEvents.WalletCreated;
import com.bni.orange.wallet.domain.DomainEvents.WalletMemberInvited;
import com.bni.orange.wallet.proto.WalletCreatedEvent;
import com.bni.orange.wallet.proto.WalletUpdatedEvent;
import com.bni.orange.wallet.proto.WalletMemberInvitedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

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