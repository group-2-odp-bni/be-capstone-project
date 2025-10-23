package com.bni.orange.wallet.messaging.aftercommit;

import com.bni.orange.wallet.domain.DomainEvents;
import com.bni.orange.wallet.messaging.WalletEventPublisher;
import com.bni.orange.wallet.model.entity.Wallet;
import com.bni.orange.wallet.proto.WalletCreatedEvent;
import com.bni.orange.wallet.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
@RequiredArgsConstructor
public class WalletAfterCommitListener {

  private final WalletRepository walletRepo;
  private final WalletEventPublisher publisher;
  private final ObjectMapper om; 
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onWalletCreated(DomainEvents.WalletCreated e) {
    Wallet w = walletRepo.findById(e.getWalletId()).orElse(null);
    if (w == null) return;

    String metadataJson = "{}";
    try {
      if (w.getMetadata() != null) {
        metadataJson = om.writeValueAsString(w.getMetadata());
      }
    } catch (Exception ignore) {}

    var payload = WalletCreatedEvent.newBuilder()
        .setWalletId(w.getId().toString())
        .setUserId(w.getUserId().toString())
        .setCurrency(w.getCurrency())
        .setStatus(w.getStatus().name())
        .setType(w.getType().name())
        .setName(w.getName() == null ? "" : w.getName())
        .setMetadataJson(metadataJson)
        .build();

    publisher.publishWalletCreated(w.getId().toString(), payload);
  }

}
