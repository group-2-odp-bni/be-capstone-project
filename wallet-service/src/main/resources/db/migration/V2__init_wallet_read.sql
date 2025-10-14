CREATE SCHEMA IF NOT EXISTS wallet_read;

CREATE TABLE wallet_read.wallets (
  id               UUID PRIMARY KEY,
  user_id          UUID                  NOT NULL,
  currency         VARCHAR               NOT NULL DEFAULT 'IDR',
  status           domain.wallet_status  NOT NULL DEFAULT 'ACTIVE',
  balance_snapshot NUMERIC(20,2)         NOT NULL DEFAULT 0,
  created_at       TIMESTAMPTZ           NOT NULL,
  updated_at       TIMESTAMPTZ           NOT NULL
);

CREATE INDEX idx_wallet_read_user   ON wallet_read.wallets (user_id);
CREATE INDEX idx_wallet_read_status ON wallet_read.wallets (status);
