CREATE SCHEMA IF NOT EXISTS wallet_oltp;

DO $$ BEGIN
  CREATE TYPE wallet_status AS ENUM ('ACTIVE','BLOCKED', 'CLOSED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS wallet_oltp.wallets (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  currency VARCHAR NOT NULL DEFAULT 'IDR',
  status wallet_status NOT NULL DEFAULT 'ACTIVE',
  balance_snapshot NUMERIC NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
