CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SCHEMA IF NOT EXISTS domain;
CREATE SCHEMA IF NOT EXISTS auth_oltp;

DO $$ BEGIN
  CREATE TYPE domain.user_status AS ENUM ('PENDING_VERIFICATION','ACTIVE','SUSPENDED','LOCKED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE OR REPLACE FUNCTION domain.set_updated_at() RETURNS trigger AS $$
BEGIN NEW.updated_at := now(); RETURN NEW; END; $$ LANGUAGE plpgsql;

CREATE TABLE auth_oltp.users (
  id                UUID PRIMARY KEY                DEFAULT gen_random_uuid(),
  phone_number      VARCHAR(32)            NOT NULL UNIQUE,
  name              VARCHAR(200)           NOT NULL,
  pin_hash          VARCHAR(255)           NOT NULL,
  status            domain.user_status     NOT NULL  DEFAULT 'PENDING_VERIFICATION',
  profile_image_url TEXT,
  email_verified    BOOLEAN                           DEFAULT FALSE,
  phone_verified    BOOLEAN                           DEFAULT FALSE,
  created_at        TIMESTAMPTZ             NOT NULL  DEFAULT now(),
  updated_at        TIMESTAMPTZ             NOT NULL  DEFAULT now(),
  last_login_at     TIMESTAMPTZ,
  CONSTRAINT chk_phone_format CHECK (phone_number ~ '^\+[1-9]\d{1,14}$')
);

CREATE TRIGGER trg_auth_users_updated_at
BEFORE UPDATE ON auth_oltp.users
FOR EACH ROW EXECUTE FUNCTION domain.set_updated_at();

CREATE INDEX idx_auth_users_phone_number ON auth_oltp.users (phone_number);

CREATE TABLE auth_oltp.refresh_tokens (
  id           UUID PRIMARY KEY                DEFAULT gen_random_uuid(),
  user_id      UUID                   NOT NULL REFERENCES auth_oltp.users (id) ON DELETE CASCADE,
  token_hash   VARCHAR(255)           NOT NULL UNIQUE,
  expiry_date  TIMESTAMPTZ            NOT NULL,
  ip_address   VARCHAR(45)            NOT NULL,
  user_agent   VARCHAR(256)           NOT NULL,
  last_used_at TIMESTAMPTZ            NOT NULL DEFAULT now(),
  is_revoked   BOOLEAN                           DEFAULT FALSE,
  revoked_at   TIMESTAMPTZ
);

CREATE INDEX idx_auth_refresh_user_active
  ON auth_oltp.refresh_tokens (user_id, is_revoked, expiry_date DESC);

CREATE INDEX idx_auth_refresh_active_hash
  ON auth_oltp.refresh_tokens (token_hash)
  WHERE is_revoked = FALSE;
