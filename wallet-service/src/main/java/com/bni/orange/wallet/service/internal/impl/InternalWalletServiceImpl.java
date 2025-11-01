package com.bni.orange.wallet.service.internal.impl;

import com.bni.orange.wallet.model.enums.InternalAction;
import com.bni.orange.wallet.model.enums.WalletMemberStatus;
import com.bni.orange.wallet.model.enums.WalletStatus;
import com.bni.orange.wallet.model.request.internal.BalanceUpdateRequest;
import com.bni.orange.wallet.model.request.internal.BalanceValidateRequest;
import com.bni.orange.wallet.model.request.internal.RoleValidateRequest;
import com.bni.orange.wallet.model.response.internal.BalanceUpdateResponse;
import com.bni.orange.wallet.model.response.internal.DefaultWalletResponse;
import com.bni.orange.wallet.model.response.internal.RoleValidateResponse;
import com.bni.orange.wallet.model.response.internal.UserWalletsResponse;
import com.bni.orange.wallet.model.response.internal.ValidationResultResponse;
import com.bni.orange.wallet.repository.UserReceivePrefsRepository;
import com.bni.orange.wallet.repository.WalletInternalRepository;
import com.bni.orange.wallet.repository.WalletMemberInternalRepository;
import com.bni.orange.wallet.repository.WalletMemberRepository;
import com.bni.orange.wallet.repository.WalletPolicyInternalRepository;
import com.bni.orange.wallet.repository.read.WalletReadRepository;
import com.bni.orange.wallet.service.internal.InternalWalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InternalWalletServiceImpl implements InternalWalletService {

  private final WalletInternalRepository walletRepo;
  private final WalletMemberInternalRepository memberRepo;
  private final WalletPolicyInternalRepository policyRepo;
  private final UserReceivePrefsRepository userReceivePrefsRepo;
  private final WalletReadRepository walletReadRepo;
  private final WalletMemberRepository walletMemberRepo;

  public InternalWalletServiceImpl(
      WalletInternalRepository walletRepo,
      WalletMemberInternalRepository memberRepo,
      WalletPolicyInternalRepository policyRepo,
      UserReceivePrefsRepository userReceivePrefsRepo,
      WalletReadRepository walletReadRepo,
      WalletMemberRepository walletMemberRepo
  ) {
    this.walletRepo = walletRepo;
    this.memberRepo = memberRepo;
    this.policyRepo = policyRepo;
    this.userReceivePrefsRepo = userReceivePrefsRepo;
    this.walletReadRepo = walletReadRepo;
    this.walletMemberRepo = walletMemberRepo;
  }

  @Override
  public ValidationResultResponse validateBalance(BalanceValidateRequest req) {
    var vw = walletRepo.viewStatusAndBalance(req.walletId()).orElse(null);
    if (vw == null) {
      return new ValidationResultResponse(false, "WALLET_NOT_FOUND", "Wallet tidak ditemukan",
          Map.of("walletId", req.walletId()));
    }
    if (vw.status() != WalletStatus.ACTIVE) {
      return new ValidationResultResponse(false, "WALLET_NOT_ACTIVE", "Wallet tidak dalam status ACTIVE",
          Map.of("status", vw.status(), "balance", vw.balanceSnapshot()));
    }
    if (req.action() == InternalAction.DEBIT && vw.balanceSnapshot().compareTo(req.amount()) < 0) {
      return new ValidationResultResponse(false, "INSUFFICIENT_BALANCE", "Saldo tidak mencukupi untuk debit",
          Map.of("balance", vw.balanceSnapshot(), "required", req.amount()));
    }
    return new ValidationResultResponse(true, "OK", "Valid",
        Map.of("balance", vw.balanceSnapshot(), "action", req.action().name()));
  }

  @Override
  @Transactional
  public BalanceUpdateResponse updateBalance(BalanceUpdateRequest req) {
    var vw = walletRepo.lockForUpdate(req.walletId()).orElse(null);
    if (vw == null) {
      return new BalanceUpdateResponse(req.walletId(), null, null, "WALLET_NOT_FOUND", "Wallet tidak ditemukan");
    }
    if (vw.status() != WalletStatus.ACTIVE) {
      return new BalanceUpdateResponse(req.walletId(), vw.balanceSnapshot(), vw.balanceSnapshot(),
          "WALLET_NOT_ACTIVE", "Wallet tidak dalam status ACTIVE");
    }

    var after = walletRepo.incrementBalanceAtomically(req.walletId(), req.delta());
    if (after.isEmpty()) {
      return new BalanceUpdateResponse(req.walletId(), vw.balanceSnapshot(), vw.balanceSnapshot(),
          "NEGATIVE_NOT_ALLOWED", "Perubahan saldo ditolak karena akan membuat saldo negatif");
    }
    return new BalanceUpdateResponse(req.walletId(), vw.balanceSnapshot(), after.get(),
        "OK", "Saldo diperbarui");
  }

  @Override
  public RoleValidateResponse validateRole(RoleValidateRequest req) {
    var mv = memberRepo.viewRoleAndStatus(req.walletId(), req.userId()).orElse(null);
    if (mv == null) {
      return new RoleValidateResponse(false, "NOT_MEMBER", "User bukan member wallet", null, Map.of());
    }
    if (mv.status() != WalletMemberStatus.ACTIVE) {
      return new RoleValidateResponse(false, "MEMBER_NOT_ACTIVE", "Membership tidak ACTIVE",
          mv.role(), Map.of("memberStatus", mv.status()));
    }

    boolean allowed;
    String code = "OK";
    String message = "Valid";
    String currency = null;

    if (req.action() == InternalAction.DEBIT) {
        var res = policyRepo.isDebitRoleAllowed(req.walletId(), mv.role());
        allowed = res.allowed();
        currency = res.currency();
        if (!allowed) { code = "ROLE_NOT_ALLOWED"; message = "Role tidak diizinkan melakukan DEBIT"; }
    } else if (req.action() == InternalAction.CREDIT) {
        var res = policyRepo.isCreditAllowed(req.walletId());
        allowed = res.allowed();
        currency = res.currency();
        if (!allowed) { code = "CREDIT_DISABLED"; message = "Wallet type tidak mengizinkan kredit eksternal"; }
    } else if (req.action() == InternalAction.ADMIN) {
        allowed = "OWNER".equals(mv.role()) || "ADMIN".equals(mv.role());
        if (!allowed) { code = "ROLE_NOT_ALLOWED"; message = "Hanya OWNER/ADMIN untuk action ADMIN"; }
    } else {
        allowed = true;
    }

    if (allowed && req.action() == InternalAction.DEBIT && req.amount() != null) {
      long perTx = memberRepo.findPerTxLimit(req.walletId(), req.userId());
      if (perTx > 0) {
        long rupiah = req.amount().setScale(0, java.math.RoundingMode.DOWN).longValue();
        if (rupiah > perTx) {
          allowed = false; code = "LIMIT_EXCEEDED"; message = "Nominal melebihi per_tx_limit_rp member";
        }
      }
    }

    var details = new java.util.HashMap<String, Object>();
    details.put("walletId", req.walletId());
    details.put("currency", currency);

    return new RoleValidateResponse(
        allowed, code, message, mv.role(),
        details
    );
  }

  @Override
  public DefaultWalletResponse getDefaultWalletByUserId(UUID userId) {
      return userReceivePrefsRepo
          .findByUserId(userId)
          .flatMap(prefs -> walletReadRepo.findById(prefs.getDefaultWalletId()))
          .map(wallet -> DefaultWalletResponse.builder()
              .userId(userId)
              .hasDefaultWallet(true)
              .walletId(wallet.getId())
              .walletName(wallet.getName())
              .walletType(wallet.getType())
              .currency(wallet.getCurrency())
              .build())
          .orElseGet(() -> DefaultWalletResponse.builder()
              .userId(userId)
              .hasDefaultWallet(false)
              .build());
  }


  @Override
  public UserWalletsResponse getWalletsByUserId(UUID userId, boolean idsOnly) {
    if (idsOnly) {
      var ids = walletMemberRepo.findWalletIdsByUserId(userId);
      return UserWalletsResponse.builder().walletIds(ids).build();
    }
    throw new UnsupportedOperationException("Full wallet details not supported via this internal method");
  }
}
