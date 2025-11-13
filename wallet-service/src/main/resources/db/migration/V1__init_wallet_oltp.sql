CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SCHEMA IF NOT EXISTS domain;
CREATE SCHEMA IF NOT EXISTS wallet_oltp;

DO $$ BEGIN
  CREATE TYPE domain.wallet_status AS ENUM ('ACTIVE','SUSPENDED','CLOSED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE OR REPLACE FUNCTION domain.set_updated_at() RETURNS trigger AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END; $$ LANGUAGE plpgsql;

CREATE TABLE wallet_oltp.wallets (
  id               UUID PRIMARY KEY               DEFAULT gen_random_uuid(),
  user_id          UUID                  NOT NULL,             -- referensi ke auth (tanpa FK lintas service/DB)
  currency         VARCHAR               NOT NULL DEFAULT 'IDR',
  status           domain.wallet_status  NOT NULL DEFAULT 'ACTIVE',
  balance_snapshot NUMERIC(20,2)         NOT NULL DEFAULT 0,
  created_at       TIMESTAMPTZ           NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ           NOT NULL DEFAULT now(),
  CONSTRAINT chk_balance_nonnegative CHECK (balance_snapshot >= 0)
);

CREATE TRIGGER trg_wallets_updated_at
BEFORE UPDATE ON wallet_oltp.wallets
FOR EACH ROW EXECUTE FUNCTION domain.set_updated_at();

CREATE INDEX idx_wallet_user   ON wallet_oltp.wallets (user_id);
CREATE INDEX idx_wallet_status ON wallet_oltp.wallets (status);
