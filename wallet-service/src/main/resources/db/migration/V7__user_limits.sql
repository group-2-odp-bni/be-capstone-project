-- Limit nominal (per-tx/day/week/month) disimpan di wallet-service, dienforce di transaction-service
CREATE TABLE IF NOT EXISTS wallet_oltp.user_limits (
  user_id                 UUID PRIMARY KEY,
  per_tx_max_rp           BIGINT NOT NULL DEFAULT 0,   -- 0 = no cap
  daily_max_rp            BIGINT NOT NULL DEFAULT 0,
  weekly_max_rp           BIGINT NOT NULL DEFAULT 0,
  monthly_max_rp          BIGINT NOT NULL DEFAULT 0,
  per_tx_min_rp           BIGINT NOT NULL DEFAULT 0,
  enforce_per_tx          BOOLEAN NOT NULL DEFAULT TRUE,
  enforce_daily           BOOLEAN NOT NULL DEFAULT TRUE,
  enforce_weekly          BOOLEAN NOT NULL DEFAULT TRUE,
  enforce_monthly         BOOLEAN NOT NULL DEFAULT TRUE,
  effective_from          TIMESTAMPTZ,
  effective_through       TIMESTAMPTZ,
  timezone                VARCHAR(64) NOT NULL DEFAULT 'Asia/Jakarta',
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_user_limits_nonneg
    CHECK (per_tx_max_rp >= 0 AND daily_max_rp >= 0 AND weekly_max_rp >= 0 AND monthly_max_rp >= 0
           AND per_tx_min_rp >= 0)
);

DROP TRIGGER IF EXISTS trg_user_limits_updated_at ON wallet_oltp.user_limits;
CREATE TRIGGER trg_user_limits_updated_at
BEFORE UPDATE ON wallet_oltp.user_limits
FOR EACH ROW EXECUTE FUNCTION domain.set_updated_at();


CREATE TABLE IF NOT EXISTS wallet_read.user_limits (
  user_id           UUID PRIMARY KEY,
  per_tx_max_rp     BIGINT NOT NULL,
  daily_max_rp      BIGINT NOT NULL,
  weekly_max_rp     BIGINT NOT NULL,
  monthly_max_rp    BIGINT NOT NULL,
  per_tx_min_rp     BIGINT NOT NULL,
  enforce_per_tx    BOOLEAN NOT NULL,
  enforce_daily     BOOLEAN NOT NULL,
  enforce_weekly    BOOLEAN NOT NULL,
  enforce_monthly   BOOLEAN NOT NULL,
  effective_from    TIMESTAMPTZ,
  effective_through TIMESTAMPTZ,
  timezone          VARCHAR(64) NOT NULL,
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
