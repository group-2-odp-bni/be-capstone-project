CREATE SCHEMA IF NOT EXISTS transfer_read;

CREATE TABLE IF NOT EXISTS transfer_read.wallet_status_projection (
  wallet_id   UUID PRIMARY KEY,
  status      domain.wallet_status NOT NULL,
  updated_at  TIMESTAMPTZ          NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_transfer_walletproj_status ON transfer_read.wallet_status_projection (status);

CREATE TABLE IF NOT EXISTS transfer_read.transactions (
  id           UUID PRIMARY KEY,
  wallet_id    UUID                  NOT NULL,
  trx_id       VARCHAR               NOT NULL,
  type         domain.tx_type        NOT NULL,
  amount       NUMERIC(20,2)         NOT NULL,
  currency     VARCHAR               NOT NULL DEFAULT 'IDR',
  status       domain.tx_status      NOT NULL DEFAULT 'PENDING',
  initiated_by UUID,
  created_at   TIMESTAMPTZ           NOT NULL,
  updated_at   TIMESTAMPTZ           NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_transfer_read_wallet_ts ON transfer_read.transactions (wallet_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transfer_read_status_ts ON transfer_read.transactions (status, created_at DESC);
