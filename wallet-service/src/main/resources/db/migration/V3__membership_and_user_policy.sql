CREATE TABLE IF NOT EXISTS wallet_oltp.wallet_members (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  wallet_id        UUID NOT NULL REFERENCES wallet_oltp.wallets(id) ON DELETE CASCADE,
  user_id          UUID NOT NULL,
  role             domain.wallet_member_role   NOT NULL,
  status           domain.wallet_member_status NOT NULL DEFAULT 'INVITED',
  daily_limit_rp   BIGINT NOT NULL DEFAULT 0,
  monthly_limit_rp BIGINT NOT NULL DEFAULT 0,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_wallet_member UNIQUE (wallet_id, user_id),
  CONSTRAINT chk_member_limits_nonneg CHECK (daily_limit_rp >= 0 AND monthly_limit_rp >= 0)
);

DROP TRIGGER IF EXISTS trg_wallet_members_updated_at ON wallet_oltp.wallet_members;
CREATE TRIGGER trg_wallet_members_updated_at
BEFORE UPDATE ON wallet_oltp.wallet_members
FOR EACH ROW EXECUTE FUNCTION domain.set_updated_at();

CREATE INDEX IF NOT EXISTS idx_wallet_members_wallet  ON wallet_oltp.wallet_members (wallet_id);
CREATE INDEX IF NOT EXISTS idx_wallet_members_user    ON wallet_oltp.wallet_members (user_id);
CREATE INDEX IF NOT EXISTS idx_wallet_members_role    ON wallet_oltp.wallet_members (role);
CREATE INDEX IF NOT EXISTS idx_wallet_members_status  ON wallet_oltp.wallet_members (status);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes
    WHERE schemaname='wallet_oltp' AND indexname='ux_one_owner_per_wallet'
  ) THEN
    CREATE UNIQUE INDEX ux_one_owner_per_wallet
      ON wallet_oltp.wallet_members (wallet_id)
      WHERE role='OWNER' AND status IN ('INVITED','ACTIVE');
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS wallet_oltp.wallet_user_limits (
  user_id               UUID PRIMARY KEY,
  wallet_count_limit    INT  NOT NULL DEFAULT 5,   
  created_wallet_limit  INT  NOT NULL DEFAULT 10,  
  shared_member_limit   INT  NOT NULL DEFAULT 5,   
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_wallet_user_limits_updated_at ON wallet_oltp.wallet_user_limits;
CREATE TRIGGER trg_wallet_user_limits_updated_at
BEFORE UPDATE ON wallet_oltp.wallet_user_limits
FOR EACH ROW EXECUTE FUNCTION domain.set_updated_at();
