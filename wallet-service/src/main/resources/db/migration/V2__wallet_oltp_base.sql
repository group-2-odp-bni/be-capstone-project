CREATE TABLE IF NOT EXISTS wallet_oltp.wallets (
  id               UUID PRIMARY KEY               DEFAULT gen_random_uuid(),
  user_id          UUID                  NOT NULL,              
  currency         VARCHAR               NOT NULL DEFAULT 'IDR',
  status           domain.wallet_status  NOT NULL DEFAULT 'ACTIVE',
  balance_snapshot NUMERIC(20,2)         NOT NULL DEFAULT 0,
  type             domain.wallet_type    NOT NULL DEFAULT 'PERSONAL', 
  name             VARCHAR(160),
  metadata         JSONB                 NOT NULL DEFAULT '{}'::jsonb,
  created_at       TIMESTAMPTZ           NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ           NOT NULL DEFAULT now(),
  CONSTRAINT chk_balance_nonnegative CHECK (balance_snapshot >= 0)
);

DROP TRIGGER IF EXISTS trg_wallets_updated_at ON wallet_oltp.wallets;
CREATE TRIGGER trg_wallets_updated_at
BEFORE UPDATE ON wallet_oltp.wallets
FOR EACH ROW EXECUTE FUNCTION domain.set_updated_at();

CREATE INDEX IF NOT EXISTS idx_wallet_user    ON wallet_oltp.wallets (user_id);
CREATE INDEX IF NOT EXISTS idx_wallet_status  ON wallet_oltp.wallets (status);
CREATE INDEX IF NOT EXISTS idx_wallet_type    ON wallet_oltp.wallets (type);

COMMENT ON COLUMN wallet_oltp.wallets.metadata IS
'Ruang fleksibel untuk UX/purpose/warna/icon/notifikasi/policy override non-kritis.';
