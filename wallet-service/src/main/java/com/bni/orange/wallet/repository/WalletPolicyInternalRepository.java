package com.bni.orange.wallet.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class WalletPolicyInternalRepository {
  public record PolicyCheckResult(boolean allowed, String currency) {}

  private final NamedParameterJdbcTemplate jdbc;

  public WalletPolicyInternalRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public PolicyCheckResult isCreditAllowed(UUID walletId) {
    var sql = """
      SELECT p.allow_external_credit AS allow_credit, w.currency
      FROM wallet_oltp.wallet_type_policy p
      JOIN wallet_oltp.wallets w ON w.type = p.type
      WHERE w.id = :wid
      """;
    var p = new MapSqlParameterSource("wid", walletId);
    return jdbc.query(sql, p, rs -> {
      if (!rs.next()) return new PolicyCheckResult(false, null);
      boolean ok = rs.getBoolean("allow_credit");
      String ccy = rs.getString("currency");
      return new PolicyCheckResult(ok, ccy);
    });
  }

  public PolicyCheckResult isDebitRoleAllowed(UUID walletId, String role) {
    var sql = """
      SELECT jsonb_exists(p.allow_member_debit_roles, :role) AS ok, w.currency
      FROM wallet_oltp.wallet_type_policy p
      JOIN wallet_oltp.wallets w ON w.type = p.type
      WHERE w.id = :wid
      """;
    var p = new MapSqlParameterSource()
        .addValue("wid", walletId)
        .addValue("role", role);
    return jdbc.query(sql, p, rs -> {
      if (!rs.next()) return new PolicyCheckResult(false, null);
      boolean ok = rs.getBoolean("ok");
      String ccy = rs.getString("currency");
      return new PolicyCheckResult(ok, ccy);
    });
  }

  public Optional<String> getWalletType(UUID walletId) {
    var sql = "SELECT w.type::text AS type FROM wallet_oltp.wallets w WHERE w.id = :wid";
    var p = new MapSqlParameterSource("wid", walletId);
    String t = jdbc.query(sql, p, rs -> rs.next() ? rs.getString("type") : null);
    return Optional.ofNullable(t);
  }
}
