package com.bni.orange.wallet.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

public class DomainEvents {

  @Getter @RequiredArgsConstructor
  public static class WalletCreated {
    private final UUID walletId;
    private final UUID userId;
  }

  @Getter @RequiredArgsConstructor
  public static class WalletUpdated {
    private final UUID walletId;
  }
}
