package com.bni.orange.wallet.repository;

import com.bni.orange.wallet.model.enums.WalletMemberStatus;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class WalletMemberInternalRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public record MemberView(String role, WalletMemberStatus status) {}

  public WalletMemberInternalRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<MemberView> viewRoleAndStatus(UUID walletId, UUID userId) {
    var sql = """
      SELECT m.role::text AS role, m.status::text AS status
      FROM wallet_oltp.wallet_members m
      WHERE m.wallet_id = :wid AND m.user_id = :uid
      """;
    var p = new MapSqlParameterSource()
        .addValue("wid", walletId)
        .addValue("uid", userId);

    try {
      return Optional.ofNullable(jdbc.query(sql, p, rs -> {
        if (!rs.next()) return null;
        return new MemberView(
            rs.getString("role"),
            WalletMemberStatus.valueOf(rs.getString("status"))
        );
      }));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  public long findPerTxLimit(UUID walletId, UUID userId) {
    var sql = """
      SELECT m.per_tx_limit_rp
      FROM wallet_oltp.wallet_members m
      WHERE m.wallet_id = :wid AND m.user_id = :uid
      """;
    var p = new MapSqlParameterSource()
        .addValue("wid", walletId)
        .addValue("uid", userId);
    Long val = jdbc.query(sql, p, rs -> rs.next() ? rs.getLong("per_tx_limit_rp") : null);
    return val == null ? 0L : val;
  }
}
