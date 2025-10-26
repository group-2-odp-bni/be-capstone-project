package com.bni.orange.wallet.service.command.impl;

import com.bni.orange.wallet.model.entity.UserReceivePrefs;
import com.bni.orange.wallet.model.entity.read.WalletRead;
import com.bni.orange.wallet.model.request.receive.SetDefaultReceiveRequest;
import com.bni.orange.wallet.model.response.receive.DefaultReceiveResponse;
import com.bni.orange.wallet.repository.UserReceivePrefsRepository;
import com.bni.orange.wallet.repository.read.UserWalletReadRepository;
import com.bni.orange.wallet.repository.read.WalletReadRepository;
import com.bni.orange.wallet.service.command.ReceiveCommandService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReceiveCommandServiceImpl implements ReceiveCommandService {

  private final UserReceivePrefsRepository prefsRepo;
  private final UserWalletReadRepository userWalletReadRepo;
  private final WalletReadRepository walletReadRepo;

  @Override
  @Transactional
  public DefaultReceiveResponse setDefaultReceiveWallet(SetDefaultReceiveRequest req) {
    var userId = currentUserId();
    var walletId = req.getWalletId();

    var membership = userWalletReadRepo.findByUserIdAndWalletId(userId, walletId)
        .orElseThrow(() -> new AccessDeniedException("You are not a member of this wallet"));

    var prefs = prefsRepo.findById(userId)
        .orElse(UserReceivePrefs.builder().userId(userId).build());
    prefs.setDefaultWalletId(walletId);
    prefs.setUpdatedAt(OffsetDateTime.now());
    prefsRepo.save(prefs);

    String walletName = walletReadRepo.findById(walletId)
        .map(WalletRead::getName)
        .orElse(null);

    return DefaultReceiveResponse.builder()
        .walletId(walletId)
        .walletName(walletName)
        .build();
  }

  private UUID currentUserId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
      String sub = jwt.getClaimAsString("sub"); 
      return UUID.fromString(sub);
    }
    throw new AccessDeniedException("Unauthenticated");
  }
}
