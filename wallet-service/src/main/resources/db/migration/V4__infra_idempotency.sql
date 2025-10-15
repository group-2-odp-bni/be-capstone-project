CREATE SCHEMA IF NOT EXISTS infra;

DO $$ BEGIN
  CREATE TYPE infra.idem_status AS ENUM ('PROCESSING','COMPLETED','FAILED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS infra.idempotency (
  id             BIGSERIAL PRIMARY KEY,
  scope          VARCHAR(64) NOT NULL,
  idem_key       VARCHAR(128) NOT NULL,
  request_hash   VARCHAR(64) NOT NULL,
  response_status INT NULL,
  response_body  JSONB NULL,
  status         infra.idem_status NOT NULL DEFAULT 'PROCESSING',
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at   TIMESTAMPTZ NULL,
  expires_at     TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '72 hours'),
  UNIQUE (scope, idem_key)
);

CREATE INDEX IF NOT EXISTS idx_idem_expires ON infra.idempotency(expires_at);
