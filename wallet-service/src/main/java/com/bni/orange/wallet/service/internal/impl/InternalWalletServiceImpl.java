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


import com.bni.orange.wallet.repository.read.UserLimitsReadRepository;
import com.bni.orange.wallet.service.command.LimitCounterService;
import com.bni.orange.wallet.model.enums.PeriodType;
import com.bni.orange.wallet.utils.limits.LimitBuckets; 



import com.bni.orange.wallet.repository.read.WalletReadRepository;
import com.bni.orange.wallet.service.internal.InternalWalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InternalWalletServiceImpl implements InternalWalletService {

  private final WalletInternalRepository walletRepo;
  private final WalletMemberInternalRepository memberRepo;
  private final WalletPolicyInternalRepository policyRepo;
  private final WalletReadRepository walletReadRepo;
  private final UserReceivePrefsRepository userReceivePrefsRepo;
  private final WalletMemberRepository walletMemberRepo;
  public record PolicyCheckResult(boolean allowed, String currency) {}
  private final UserLimitsReadRepository userLimitsReadRepo;
  private final LimitCounterService limitCounterService;

  public InternalWalletServiceImpl(
      WalletInternalRepository walletRepo,
      WalletMemberInternalRepository memberRepo,
      WalletPolicyInternalRepository policyRepo,
      WalletReadRepository walletReadRepo,
      UserLimitsReadRepository userLimitsReadRepo,
      LimitCounterService limitCounterService,
      UserReceivePrefsRepository userReceivePrefsRepo,
      WalletMemberRepository walletMemberRepo
      ) {
    this.walletRepo = walletRepo;
    this.memberRepo = memberRepo;
    this.policyRepo = policyRepo;
    this.walletReadRepo=walletReadRepo;
    this.userLimitsReadRepo = userLimitsReadRepo;
    this.limitCounterService = limitCounterService;
    this.userReceivePrefsRepo = userReceivePrefsRepo;
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
    if (req.action() == InternalAction.DEBIT) {
      var mOpt = userLimitsReadRepo.findByUserId(req.actorUserId());
      if (mOpt.isPresent()) {
        var m = mOpt.get();
        long amt = req.amount().setScale(0, java.math.RoundingMode.DOWN).longValue();
        if (m.isEnforcePerTx()) {
          if (m.getPerTxMinRp() > 0 && amt < m.getPerTxMinRp()) {
            return new ValidationResultResponse(false, "UNDER_MIN_PER_TX", "Nominal di bawah batas minimum per transaksi",
                Map.of("min", m.getPerTxMinRp(), "amount", amt));
          }
          if (m.getPerTxMaxRp() > 0 && amt > m.getPerTxMaxRp()) {
            return new ValidationResultResponse(false, "OVER_MAX_PER_TX", "Nominal melebihi batas maksimum per transaksi",
                Map.of("max", m.getPerTxMaxRp(), "amount", amt));
          }
        }

        var now = java.time.OffsetDateTime.now();
        var dayStart   = LimitBuckets.dayStart(now);
        var weekStart  = LimitBuckets.weekStart(now);
        var monthStart = LimitBuckets.monthStart(now);

        long usedDay   = limitCounterService.getUsed(req.actorUserId(), PeriodType.DAY,   dayStart);
        long usedWeek  = limitCounterService.getUsed(req.actorUserId(), PeriodType.WEEK,  weekStart);
        long usedMonth = limitCounterService.getUsed(req.actorUserId(), PeriodType.MONTH, monthStart);

        if (m.isEnforceDaily() && m.getDailyMaxRp() > 0 && usedDay + amt > m.getDailyMaxRp()) {
          return new ValidationResultResponse(false, "DAILY_CAP_EXCEEDED", "Batas harian terlampaui",
              Map.of("used", usedDay, "cap", m.getDailyMaxRp(), "incoming", amt));
        }
        if (m.isEnforceWeekly() && m.getWeeklyMaxRp() > 0 && usedWeek + amt > m.getWeeklyMaxRp()) {
          return new ValidationResultResponse(false, "WEEKLY_CAP_EXCEEDED", "Batas mingguan terlampaui",
              Map.of("used", usedWeek, "cap", m.getWeeklyMaxRp(), "incoming", amt));
        }
        if (m.isEnforceMonthly() && m.getMonthlyMaxRp() > 0 && usedMonth + amt > m.getMonthlyMaxRp()) {
          return new ValidationResultResponse(false, "MONTHLY_CAP_EXCEEDED", "Batas bulanan terlampaui",
              Map.of("used", usedMonth, "cap", m.getMonthlyMaxRp(), "incoming", amt));
        }
      }
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
    if (req.delta().compareTo(BigDecimal.ZERO) < 0) {
      var mOpt = userLimitsReadRepo.findByUserId(req.actorUserId());
      if (mOpt.isPresent()) {
        var m = mOpt.get();
        long amt = req.delta().abs().setScale(0, java.math.RoundingMode.DOWN).longValue();

        if (m.isEnforcePerTx()) {
          if (m.getPerTxMinRp() > 0 && amt < m.getPerTxMinRp()) {
            return new BalanceUpdateResponse(req.walletId(), vw.balanceSnapshot(), vw.balanceSnapshot(),
                "UNDER_MIN_PER_TX", "Nominal di bawah batas minimum per transaksi");
          }
          if (m.getPerTxMaxRp() > 0 && amt > m.getPerTxMaxRp()) {
            return new BalanceUpdateResponse(req.walletId(), vw.balanceSnapshot(), vw.balanceSnapshot(),
                "OVER_MAX_PER_TX", "Nominal melebihi batas maksimum per transaksi");
          }
        }

        var now = java.time.OffsetDateTime.now();
        var dayStart   = LimitBuckets.dayStart(now);
        var weekStart  = LimitBuckets.weekStart(now);
        var monthStart = LimitBuckets.monthStart(now);

        long usedDay   = limitCounterService.getUsed(req.actorUserId(), PeriodType.DAY,   dayStart);
        long usedWeek  = limitCounterService.getUsed(req.actorUserId(), PeriodType.WEEK,  weekStart);
        long usedMonth = limitCounterService.getUsed(req.actorUserId(), PeriodType.MONTH, monthStart);

        if (m.isEnforceDaily() && m.getDailyMaxRp() > 0 && usedDay + amt > m.getDailyMaxRp()) {
          return new BalanceUpdateResponse(req.walletId(), vw.balanceSnapshot(), vw.balanceSnapshot(),
              "DAILY_CAP_EXCEEDED", "Batas harian terlampaui");
        }
        if (m.isEnforceWeekly() && m.getWeeklyMaxRp() > 0 && usedWeek + amt > m.getWeeklyMaxRp()) {
          return new BalanceUpdateResponse(req.walletId(), vw.balanceSnapshot(), vw.balanceSnapshot(),
              "WEEKLY_CAP_EXCEEDED", "Batas mingguan terlampaui");
        }
        if (m.isEnforceMonthly() && m.getMonthlyMaxRp() > 0 && usedMonth + amt > m.getMonthlyMaxRp()) {
          return new BalanceUpdateResponse(req.walletId(), vw.balanceSnapshot(), vw.balanceSnapshot(),
              "MONTHLY_CAP_EXCEEDED", "Batas bulanan terlampaui");
        }
      }
    }

    var after = walletRepo.incrementBalanceAtomically(req.walletId(), req.delta());
    if (after.isEmpty()) {
      return new BalanceUpdateResponse(req.walletId(), vw.balanceSnapshot(), vw.balanceSnapshot(),
          "NEGATIVE_NOT_ALLOWED", "Perubahan saldo ditolak karena akan membuat saldo negatif");
    }
    walletReadRepo.upsertBalanceSnapshot(req.walletId(), after.get());
    if (req.delta().compareTo(BigDecimal.ZERO) < 0) {
      long amt = req.delta().abs().setScale(0, RoundingMode.DOWN).longValueExact();
      var now = java.time.OffsetDateTime.now();
      var dayStart   = LimitBuckets.dayStart(now);
      var weekStart  = LimitBuckets.weekStart(now);
      var monthStart = LimitBuckets.monthStart(now);
      limitCounterService.addUsage(req.actorUserId(), PeriodType.DAY,   dayStart,   amt);
      limitCounterService.addUsage(req.actorUserId(), PeriodType.WEEK,  weekStart,  amt);
      limitCounterService.addUsage(req.actorUserId(), PeriodType.MONTH, monthStart, amt);
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
