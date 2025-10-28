package com.bni.orange.wallet.service.query.impl;

import com.bni.orange.wallet.model.entity.read.WalletRead;
import com.bni.orange.wallet.model.response.receive.DefaultReceiveResponse;
import com.bni.orange.wallet.repository.UserReceivePrefsRepository;
import com.bni.orange.wallet.repository.read.WalletReadRepository;
import com.bni.orange.wallet.service.query.ReceiveQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReceiveQueryServiceImpl implements ReceiveQueryService {

  private final UserReceivePrefsRepository prefsRepo;
  private final WalletReadRepository walletReadRepo;

  @Override
  @Transactional(readOnly = true)
  public DefaultReceiveResponse getDefaultReceiveWallet() {
    var userId = currentUserId();

    var prefs = prefsRepo.findByUserId(userId)
        .orElseThrow(() -> new IllegalStateException("No default receive wallet set"));

    var walletName = walletReadRepo.findById(prefs.getDefaultWalletId())
        .map(WalletRead::getName)
        .orElse(null);

    return DefaultReceiveResponse.builder()
        .walletId(prefs.getDefaultWalletId())
        .walletName(walletName)
        .build();
  }

  private UUID currentUserId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
      String sub = jwt.getClaimAsString("sub"); // asumsi UUID
      return UUID.fromString(sub);
    }
    throw new AccessDeniedException("Unauthenticated");
  }
}
