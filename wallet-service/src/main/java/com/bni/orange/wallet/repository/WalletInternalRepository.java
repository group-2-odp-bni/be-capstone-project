package com.bni.orange.wallet.repository;

import com.bni.orange.wallet.model.enums.WalletStatus;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Repository
public class WalletInternalRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public record WalletView(WalletStatus status, BigDecimal balanceSnapshot) {}

  public WalletInternalRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<WalletView> viewStatusAndBalance(UUID walletId) {
    var sql = """
      SELECT status::text AS status, balance_snapshot
      FROM wallet_oltp.wallets
      WHERE id = :wid
      """;
    try {
      return Optional.ofNullable(
          jdbc.query(sql, new MapSqlParameterSource("wid", walletId), rs -> {
            if (!rs.next()) return null;
            return new WalletView(
                WalletStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("balance_snapshot")
            );
          })
      );
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  public Optional<WalletView> lockForUpdate(UUID walletId) {
    var sql = """
      SELECT status::text AS status, balance_snapshot
      FROM wallet_oltp.wallets
      WHERE id = :wid
      FOR UPDATE
      """;
    try {
      return Optional.ofNullable(
          jdbc.query(sql, new MapSqlParameterSource("wid", walletId), rs -> {
            if (!rs.next()) return null;
            return new WalletView(
                WalletStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("balance_snapshot")
            );
          })
      );
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  @Transactional
  public Optional<BigDecimal> incrementBalanceAtomically(UUID walletId, BigDecimal delta) {
    var sql = """
      UPDATE wallet_oltp.wallets
      SET balance_snapshot = balance_snapshot + :delta
      WHERE id = :wid AND (balance_snapshot + :delta) >= 0
      RETURNING balance_snapshot
      """;
    var p = new MapSqlParameterSource()
        .addValue("wid", walletId)
        .addValue("delta", delta);
    var res = jdbc.query(sql, p, (rs, i) -> rs.getBigDecimal("balance_snapshot"));
    return res.isEmpty() ? Optional.empty() : Optional.of(res.getFirst());
  }
}
