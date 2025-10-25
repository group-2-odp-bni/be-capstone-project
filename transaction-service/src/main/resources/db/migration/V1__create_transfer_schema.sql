DO
$$
BEGIN
    IF
NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'tx_type') THEN
CREATE TYPE domain.tx_type AS ENUM (
            'TRANSFER_OUT',
            'TRANSFER_IN',
            'TOP_UP',
            'PAYMENT',
            'REFUND',
            'WITHDRAWAL'
        );
END IF;
END $$;

DO
$$
BEGIN
    IF
NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'tx_status') THEN
CREATE TYPE domain.tx_status AS ENUM (
            'PENDING',
            'PROCESSING',
            'SUCCESS',
            'FAILED',
            'REVERSED'
        );
END IF;
END $$;

CREATE TABLE transaction_oltp.transactions
(
    id                 UUID PRIMARY KEY             DEFAULT gen_random_uuid(),
    transaction_ref    VARCHAR(50) UNIQUE  NOT NULL,
    idempotency_key    VARCHAR(128) UNIQUE NOT NULL,
    type domain.tx_type NOT NULL,
    status domain.tx_status NOT NULL DEFAULT 'PENDING',
    amount             NUMERIC(20, 2)      NOT NULL CHECK (amount > 0),
    fee                NUMERIC(20, 2)      NOT NULL DEFAULT 0 CHECK (fee >= 0),
    total_amount       NUMERIC(20, 2)      NOT NULL CHECK (total_amount > 0),
    currency           VARCHAR(3)          NOT NULL DEFAULT 'IDR',
    sender_user_id     UUID                NOT NULL,
    sender_wallet_id   UUID                NOT NULL,
    receiver_user_id   UUID                NOT NULL,
    receiver_wallet_id UUID                NOT NULL,
    receiver_name      VARCHAR(255),
    receiver_phone     VARCHAR(50),
    description        TEXT,
    notes              VARCHAR(255),
    metadata           JSONB,
    completed_at       TIMESTAMPTZ,
    failed_at          TIMESTAMPTZ,
    failure_reason     TEXT,
    created_at         TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

CREATE TABLE transaction_oltp.transaction_ledger
(
    id              BIGSERIAL PRIMARY KEY,
    transaction_id  UUID           NOT NULL,
    transaction_ref VARCHAR(50)    NOT NULL,
    wallet_id       UUID           NOT NULL,
    user_id         UUID           NOT NULL,
    entry_type      VARCHAR(10)    NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount          NUMERIC(20, 2) NOT NULL CHECK (amount > 0),
    balance_before  NUMERIC(20, 2) NOT NULL,
    balance_after   NUMERIC(20, 2) NOT NULL,
    description     TEXT,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_transaction FOREIGN KEY (transaction_id)
        REFERENCES transaction_oltp.transactions (id) ON DELETE CASCADE
);

CREATE TABLE transaction_oltp.quick_transfers
(
    id                       UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id                  UUID         NOT NULL,
    recipient_user_id        UUID         NOT NULL,
    recipient_name           VARCHAR(255) NOT NULL,
    recipient_phone          VARCHAR(50)  NOT NULL,
    recipient_avatar_initial VARCHAR(5),
    usage_count              INTEGER      NOT NULL DEFAULT 0 CHECK (usage_count >= 0),
    last_used_at             TIMESTAMPTZ,
    display_order            INTEGER      NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_recipient UNIQUE (user_id, recipient_user_id)
);

CREATE INDEX idx_trx_sender_user ON transaction_oltp.transactions (sender_user_id, created_at DESC);
CREATE INDEX idx_trx_receiver_user ON transaction_oltp.transactions (receiver_user_id, created_at DESC);
CREATE INDEX idx_trx_sender_wallet ON transaction_oltp.transactions (sender_wallet_id);
CREATE INDEX idx_trx_receiver_wallet ON transaction_oltp.transactions (receiver_wallet_id);
CREATE INDEX idx_trx_status ON transaction_oltp.transactions (status);
CREATE INDEX idx_trx_created_at ON transaction_oltp.transactions (created_at DESC);
CREATE INDEX idx_trx_ref ON transaction_oltp.transactions (transaction_ref);
CREATE INDEX idx_trx_idem_key ON transaction_oltp.transactions (idempotency_key);
CREATE INDEX idx_trx_type_status ON transaction_oltp.transactions (type, status);

CREATE INDEX idx_ledger_transaction ON transaction_oltp.transaction_ledger (transaction_id);
CREATE INDEX idx_ledger_wallet ON transaction_oltp.transaction_ledger (wallet_id, created_at DESC);
CREATE INDEX idx_ledger_user ON transaction_oltp.transaction_ledger (user_id, created_at DESC);
CREATE INDEX idx_ledger_created_at ON transaction_oltp.transaction_ledger (created_at DESC);
CREATE INDEX idx_ledger_ref ON transaction_oltp.transaction_ledger (transaction_ref);

CREATE INDEX idx_qt_user_id ON transaction_oltp.quick_transfers (user_id);
CREATE INDEX idx_qt_user_usage ON transaction_oltp.quick_transfers (user_id, usage_count DESC);
CREATE INDEX idx_qt_user_recent ON transaction_oltp.quick_transfers (user_id, last_used_at DESC NULLS LAST);
CREATE INDEX idx_qt_user_order ON transaction_oltp.quick_transfers (user_id, display_order ASC);
CREATE INDEX idx_qt_recipient ON transaction_oltp.quick_transfers (recipient_user_id);

DO
$$
BEGIN
    IF
NOT EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'set_updated_at') THEN
        CREATE
OR REPLACE FUNCTION domain.set_updated_at()
        RETURNS TRIGGER AS '
        BEGIN
            NEW.updated_at = NOW();
            RETURN NEW;
        END;
        ' LANGUAGE plpgsql;
END IF;
END $$;

CREATE TRIGGER trg_transactions_updated_at
    BEFORE UPDATE
    ON transaction_oltp.transactions
    FOR EACH ROW
    EXECUTE FUNCTION domain.set_updated_at();

CREATE TRIGGER trg_quick_transfers_updated_at
    BEFORE UPDATE
    ON transaction_oltp.quick_transfers
    FOR EACH ROW
    EXECUTE FUNCTION domain.set_updated_at();

COMMENT
ON TABLE transaction_oltp.transactions IS 'Main transaction table for P2P transfers (Write Model)';
COMMENT
ON COLUMN transaction_oltp.transactions.transaction_ref IS 'Human-readable reference: TRX-20251019-XXXXX';
COMMENT
ON COLUMN transaction_oltp.transactions.idempotency_key IS 'Client-provided key for idempotent operations';
COMMENT
ON COLUMN transaction_oltp.transactions.total_amount IS 'amount + fee';
COMMENT
ON COLUMN transaction_oltp.transactions.receiver_name IS 'Cached from user-service to avoid repeated lookups';
COMMENT
ON COLUMN transaction_oltp.transactions.metadata IS 'Flexible JSON data for future extensibility';

COMMENT
ON TABLE transaction_oltp.transaction_ledger IS 'Double-entry ledger for audit trail and reconciliation';
COMMENT
ON COLUMN transaction_oltp.transaction_ledger.entry_type IS 'DEBIT (negative) or CREDIT (positive)';
COMMENT
ON COLUMN transaction_oltp.transaction_ledger.balance_before IS 'Wallet balance before this entry';
COMMENT
ON COLUMN transaction_oltp.transaction_ledger.balance_after IS 'Wallet balance after this entry';

COMMENT
ON TABLE transaction_oltp.quick_transfers IS 'Quick transfer (favorite recipients) feature';
COMMENT
ON COLUMN transaction_oltp.quick_transfers.usage_count IS 'Number of times transferred to this recipient';
COMMENT
ON COLUMN transaction_oltp.quick_transfers.last_used_at IS 'Last transfer timestamp for recency sorting';
COMMENT
ON COLUMN transaction_oltp.quick_transfers.display_order IS 'User-defined order for manual sorting';
COMMENT
ON COLUMN transaction_oltp.quick_transfers.recipient_avatar_initial IS 'Auto-generated initials from recipient name';