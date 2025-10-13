CREATE SCHEMA IF NOT EXISTS transaction_read;

CREATE TABLE IF NOT EXISTS transaction_read.wallet_status_projection
(
    wallet_id  UUID PRIMARY KEY,
    status     domain.wallet_status NOT NULL,
    updated_at TIMESTAMPTZ          NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_transfer_walletproj_status ON transaction_read.wallet_status_projection (status);

CREATE TABLE IF NOT EXISTS transaction_read.transactions
(
    id           UUID PRIMARY KEY,
    wallet_id    UUID             NOT NULL,
    trx_id       VARCHAR          NOT NULL,
    type         domain.tx_type   NOT NULL,
    amount       NUMERIC(20, 2)   NOT NULL,
    currency     VARCHAR          NOT NULL DEFAULT 'IDR',
    status       domain.tx_status NOT NULL DEFAULT 'PENDING',
    initiated_by UUID,
    created_at   TIMESTAMPTZ      NOT NULL,
    updated_at   TIMESTAMPTZ      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_transaction_read_wallet_ts ON transaction_read.transactions (wallet_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transaction_read_status_ts ON transaction_read.transactions (status, created_at DESC);