CREATE SCHEMA IF NOT EXISTS wallet_read;

CREATE TABLE IF NOT EXISTS wallet_read.wallets (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  currency VARCHAR NOT NULL DEFAULT 'IDR',
  status wallet_status NOT NULL DEFAULT 'ACTIVE',
  balance_snapshot NUMERIC NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
