-- ============================================================================
-- Transaction Service Database Schema - Complete Initial Migration
-- ============================================================================
-- This migration creates all necessary tables, types, indexes, and constraints
-- for the transaction service including transfers, top-ups, and ledger tracking
-- ============================================================================

-- ============================================================================
-- SECTION 1: CREATE ENUMS
-- ============================================================================

-- Transaction Type Enum
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'tx_type') THEN
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

-- Transaction Status Enum
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'tx_status') THEN
        CREATE TYPE domain.tx_status AS ENUM (
            'PENDING',
            'PROCESSING',
            'SUCCESS',
            'FAILED',
            'REVERSED'
        );
    END IF;
END $$;

-- Virtual Account Status Enum
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'va_status') THEN
        CREATE TYPE domain.va_status AS ENUM (
            'ACTIVE',
            'PAID',
            'EXPIRED',
            'CANCELLED'
        );
    END IF;
END $$;

-- Payment Provider Enum
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'payment_provider') THEN
        CREATE TYPE domain.payment_provider AS ENUM (
            'BNI_VA',
            'MANDIRI_VA',
            'BCA_VA',
            'PERMATA_VA'
        );
    END IF;
END $$;

-- ============================================================================
-- SECTION 2: CREATE TABLES
-- ============================================================================

-- Main Transactions Table (Dual-Record Model)
CREATE TABLE transaction_oltp.transactions
(
    id                     UUID PRIMARY KEY             DEFAULT gen_random_uuid(),
    transaction_ref        VARCHAR(50)         NOT NULL,
    idempotency_key        VARCHAR(128) UNIQUE NOT NULL,
    type                   domain.tx_type      NOT NULL,
    status                 domain.tx_status    NOT NULL DEFAULT 'PENDING',
    amount                 NUMERIC(20, 2)      NOT NULL CHECK (amount > 0),
    fee                    NUMERIC(20, 2)      NOT NULL DEFAULT 0 CHECK (fee >= 0),
    total_amount           NUMERIC(20, 2)      NOT NULL CHECK (total_amount > 0),
    currency               VARCHAR(3)          NOT NULL DEFAULT 'IDR',

    -- Dual-record fields (owner perspective)
    user_id                UUID                NOT NULL,
    wallet_id              UUID                NOT NULL,
    counterparty_user_id   UUID,
    counterparty_wallet_id UUID,
    counterparty_name      VARCHAR(255),
    counterparty_phone     VARCHAR(50),

    -- Additional fields
    description            TEXT,
    notes                  VARCHAR(255),
    metadata               JSONB,

    -- Timestamps
    completed_at           TIMESTAMPTZ,
    failed_at              TIMESTAMPTZ,
    failure_reason         TEXT,
    created_at             TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ         NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT uq_transaction_ref_type UNIQUE (transaction_ref, type)
);

-- Transaction Ledger (Double-Entry Bookkeeping)
CREATE TABLE transaction_oltp.transaction_ledger
(
    id                    BIGSERIAL PRIMARY KEY,
    transaction_id        UUID           NOT NULL,
    transaction_ref       VARCHAR(50)    NOT NULL,
    wallet_id             UUID           NOT NULL,
    user_id               UUID           NOT NULL,
    performed_by_user_id  UUID,
    entry_type            VARCHAR(10)    NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount                NUMERIC(20, 2) NOT NULL CHECK (amount > 0),
    balance_before        NUMERIC(20, 2) NOT NULL,
    balance_after         NUMERIC(20, 2) NOT NULL,
    description           TEXT,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_transaction FOREIGN KEY (transaction_id)
        REFERENCES transaction_oltp.transactions (id) ON DELETE CASCADE
);

-- Virtual Accounts Table (Top-Up)
CREATE TABLE transaction_oltp.virtual_accounts
(
    id                   UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    va_number            VARCHAR(20) UNIQUE       NOT NULL,
    transaction_id       UUID UNIQUE              NOT NULL,
    user_id              UUID                     NOT NULL,
    wallet_id            UUID                     NOT NULL,
    provider             domain.payment_provider  NOT NULL,
    status               domain.va_status         NOT NULL DEFAULT 'ACTIVE',
    amount               NUMERIC(20, 2)           NOT NULL CHECK (amount > 0),
    paid_amount          NUMERIC(20, 2)                    DEFAULT 0 CHECK (paid_amount >= 0),
    expires_at           TIMESTAMPTZ              NOT NULL,
    paid_at              TIMESTAMPTZ,
    expired_at           TIMESTAMPTZ,
    cancelled_at         TIMESTAMPTZ,
    callback_received_at TIMESTAMPTZ,
    callback_payload     JSONB,
    metadata             JSONB,
    created_at           TIMESTAMPTZ              NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ              NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_va_transaction FOREIGN KEY (transaction_id)
        REFERENCES transaction_oltp.transactions (id) ON DELETE CASCADE
);

-- Top-Up Provider Configurations
CREATE TABLE transaction_oltp.topup_configs
(
    id              UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    provider        domain.payment_provider  UNIQUE   NOT NULL,
    provider_name   VARCHAR(100)             NOT NULL,
    is_active       BOOLEAN                  NOT NULL DEFAULT true,
    min_amount      NUMERIC(20, 2)           NOT NULL CHECK (min_amount > 0),
    max_amount      NUMERIC(20, 2)           NOT NULL CHECK (max_amount > 0),
    fee_amount      NUMERIC(20, 2)           NOT NULL DEFAULT 0 CHECK (fee_amount >= 0),
    fee_percentage  NUMERIC(5, 2)            NOT NULL DEFAULT 0 CHECK (fee_percentage >= 0 AND fee_percentage <= 100),
    va_expiry_hours INTEGER                  NOT NULL DEFAULT 24 CHECK (va_expiry_hours > 0),
    va_prefix       VARCHAR(5)               NOT NULL,
    icon_url        TEXT,
    display_order   INTEGER                  NOT NULL DEFAULT 0,
    provider_config JSONB,
    created_at      TIMESTAMPTZ              NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ              NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_min_max CHECK (min_amount < max_amount)
);

-- Quick Transfers (Favorites)
CREATE TABLE transaction_oltp.quick_transfers
(
    id                       UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id                  UUID         NOT NULL,
    wallet_id                UUID,
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

-- ============================================================================
-- SECTION 3: CREATE INDEXES
-- ============================================================================

-- Transactions Indexes
CREATE INDEX idx_trx_user_id ON transaction_oltp.transactions (user_id, created_at DESC);
CREATE INDEX idx_trx_wallet_id ON transaction_oltp.transactions (wallet_id);
CREATE INDEX idx_trx_counterparty_user ON transaction_oltp.transactions (counterparty_user_id);
CREATE INDEX idx_trx_status ON transaction_oltp.transactions (status);
CREATE INDEX idx_trx_created_at ON transaction_oltp.transactions (created_at DESC);
CREATE INDEX idx_trx_ref ON transaction_oltp.transactions (transaction_ref);
CREATE INDEX idx_trx_idem_key ON transaction_oltp.transactions (idempotency_key);
CREATE INDEX idx_trx_type_status ON transaction_oltp.transactions (type, status);
CREATE INDEX idx_trx_user_type ON transaction_oltp.transactions (user_id, type);
CREATE INDEX idx_trx_user_status ON transaction_oltp.transactions (user_id, status);

-- Ledger Indexes
CREATE INDEX idx_ledger_transaction ON transaction_oltp.transaction_ledger (transaction_id);
CREATE INDEX idx_ledger_wallet ON transaction_oltp.transaction_ledger (wallet_id, created_at DESC);
CREATE INDEX idx_ledger_user ON transaction_oltp.transaction_ledger (user_id, created_at DESC);
CREATE INDEX idx_ledger_performed_by ON transaction_oltp.transaction_ledger (performed_by_user_id);
CREATE INDEX idx_ledger_created_at ON transaction_oltp.transaction_ledger (created_at DESC);
CREATE INDEX idx_ledger_ref ON transaction_oltp.transaction_ledger (transaction_ref);

-- Virtual Accounts Indexes
CREATE INDEX idx_va_number ON transaction_oltp.virtual_accounts (va_number);
CREATE INDEX idx_va_transaction ON transaction_oltp.virtual_accounts (transaction_id);
CREATE INDEX idx_va_user ON transaction_oltp.virtual_accounts (user_id, created_at DESC);
CREATE INDEX idx_va_status ON transaction_oltp.virtual_accounts (status);
CREATE INDEX idx_va_expires_at ON transaction_oltp.virtual_accounts (expires_at);
CREATE INDEX idx_va_provider_status ON transaction_oltp.virtual_accounts (provider, status);
CREATE INDEX idx_va_created_at ON transaction_oltp.virtual_accounts (created_at DESC);

-- Top-Up Configs Indexes
CREATE INDEX idx_topup_config_provider ON transaction_oltp.topup_configs (provider);
CREATE INDEX idx_topup_config_active ON transaction_oltp.topup_configs (is_active, display_order);

-- Quick Transfers Indexes
CREATE INDEX idx_qt_user_id ON transaction_oltp.quick_transfers (user_id);
CREATE INDEX idx_qt_wallet_id ON transaction_oltp.quick_transfers (wallet_id);
CREATE INDEX idx_qt_user_usage ON transaction_oltp.quick_transfers (user_id, usage_count DESC);
CREATE INDEX idx_qt_user_recent ON transaction_oltp.quick_transfers (user_id, last_used_at DESC NULLS LAST);
CREATE INDEX idx_qt_user_order ON transaction_oltp.quick_transfers (user_id, display_order ASC);
CREATE INDEX idx_qt_wallet_usage ON transaction_oltp.quick_transfers (wallet_id, usage_count DESC) WHERE wallet_id IS NOT NULL;
CREATE INDEX idx_qt_wallet_recent ON transaction_oltp.quick_transfers (wallet_id, last_used_at DESC NULLS LAST) WHERE wallet_id IS NOT NULL;
CREATE INDEX idx_qt_recipient ON transaction_oltp.quick_transfers (recipient_user_id);

-- ============================================================================
-- SECTION 4: CREATE TRIGGERS
-- ============================================================================

-- Updated At Trigger Function
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'set_updated_at') THEN
        CREATE OR REPLACE FUNCTION domain.set_updated_at()
        RETURNS TRIGGER AS '
        BEGIN
            NEW.updated_at = NOW();
            RETURN NEW;
        END;
        ' LANGUAGE plpgsql;
    END IF;
END $$;

-- Apply Triggers
CREATE TRIGGER trg_transactions_updated_at
    BEFORE UPDATE ON transaction_oltp.transactions
    FOR EACH ROW EXECUTE FUNCTION domain.set_updated_at();

CREATE TRIGGER trg_virtual_accounts_updated_at
    BEFORE UPDATE ON transaction_oltp.virtual_accounts
    FOR EACH ROW EXECUTE FUNCTION domain.set_updated_at();

CREATE TRIGGER trg_topup_configs_updated_at
    BEFORE UPDATE ON transaction_oltp.topup_configs
    FOR EACH ROW EXECUTE FUNCTION domain.set_updated_at();

CREATE TRIGGER trg_quick_transfers_updated_at
    BEFORE UPDATE ON transaction_oltp.quick_transfers
    FOR EACH ROW EXECUTE FUNCTION domain.set_updated_at();

-- ============================================================================
-- SECTION 5: ADD COMMENTS
-- ============================================================================

-- Transactions Table
COMMENT ON TABLE transaction_oltp.transactions IS 'Main transaction table using dual-record model (separate records for sender/receiver)';
COMMENT ON COLUMN transaction_oltp.transactions.transaction_ref IS 'Human-readable reference shared between sender/receiver records: TRX-20251103-XXXXX';
COMMENT ON COLUMN transaction_oltp.transactions.idempotency_key IS 'Client-provided key for idempotent operations';
COMMENT ON COLUMN transaction_oltp.transactions.total_amount IS 'amount + fee';
COMMENT ON COLUMN transaction_oltp.transactions.user_id IS 'Primary user (owner) of this transaction record';
COMMENT ON COLUMN transaction_oltp.transactions.wallet_id IS 'Primary wallet involved in this transaction';
COMMENT ON COLUMN transaction_oltp.transactions.counterparty_user_id IS 'The other party in the transaction (null for top-up)';
COMMENT ON COLUMN transaction_oltp.transactions.counterparty_wallet_id IS 'The other party wallet (null for top-up)';
COMMENT ON COLUMN transaction_oltp.transactions.counterparty_name IS 'Name of counterparty or provider';
COMMENT ON COLUMN transaction_oltp.transactions.counterparty_phone IS 'Phone of counterparty (null for top-up/provider)';
COMMENT ON COLUMN transaction_oltp.transactions.metadata IS 'Flexible JSON data for future extensibility';

-- Transaction Ledger
COMMENT ON TABLE transaction_oltp.transaction_ledger IS 'Double-entry ledger for audit trail and reconciliation';
COMMENT ON COLUMN transaction_oltp.transaction_ledger.entry_type IS 'DEBIT (negative) or CREDIT (positive)';
COMMENT ON COLUMN transaction_oltp.transaction_ledger.balance_before IS 'Wallet balance before this entry';
COMMENT ON COLUMN transaction_oltp.transaction_ledger.balance_after IS 'Wallet balance after this entry';
COMMENT ON COLUMN transaction_oltp.transaction_ledger.performed_by_user_id IS 'User who initiated the transaction (may differ from user_id in shared wallets). user_id = wallet owner, performed_by_user_id = actual initiator';

-- Virtual Accounts
COMMENT ON TABLE transaction_oltp.virtual_accounts IS 'Virtual Account records for top-up transactions';
COMMENT ON COLUMN transaction_oltp.virtual_accounts.va_number IS '16-digit VA number generated for payment';
COMMENT ON COLUMN transaction_oltp.virtual_accounts.transaction_id IS 'Reference to parent transaction record';
COMMENT ON COLUMN transaction_oltp.virtual_accounts.expires_at IS 'VA expiration time (default 24 hours)';
COMMENT ON COLUMN transaction_oltp.virtual_accounts.callback_payload IS 'Raw webhook payload from payment provider';
COMMENT ON COLUMN transaction_oltp.virtual_accounts.metadata IS 'Additional VA metadata';

-- Top-Up Configs
COMMENT ON TABLE transaction_oltp.topup_configs IS 'Payment provider configurations for top-up';
COMMENT ON COLUMN transaction_oltp.topup_configs.provider_config IS 'Provider-specific configuration (API keys, endpoints, etc)';
COMMENT ON COLUMN transaction_oltp.topup_configs.va_prefix IS 'Prefix for VA number generation (e.g., "7152" for BNI)';
COMMENT ON COLUMN transaction_oltp.topup_configs.va_expiry_hours IS 'Default VA expiration in hours';

-- Quick Transfers
COMMENT ON TABLE transaction_oltp.quick_transfers IS 'Quick transfer (favorite recipients) feature';
COMMENT ON COLUMN transaction_oltp.quick_transfers.wallet_id IS 'Source wallet for quick transfer. Quick transfers are now per-wallet to support multi-wallet functionality';
COMMENT ON COLUMN transaction_oltp.quick_transfers.usage_count IS 'Number of times transferred to this recipient';
COMMENT ON COLUMN transaction_oltp.quick_transfers.last_used_at IS 'Last transfer timestamp for recency sorting';
COMMENT ON COLUMN transaction_oltp.quick_transfers.display_order IS 'User-defined order for manual sorting';
COMMENT ON COLUMN transaction_oltp.quick_transfers.recipient_avatar_initial IS 'Auto-generated initials from recipient name';

-- ============================================================================
-- SECTION 6: SEED DATA
-- ============================================================================

-- Insert default top-up provider configuration
INSERT INTO transaction_oltp.topup_configs (
    provider,
    provider_name,
    is_active,
    min_amount,
    max_amount,
    fee_amount,
    fee_percentage,
    va_expiry_hours,
    va_prefix,
    display_order
)
VALUES (
    'BNI_VA',
    'BNI Virtual Account',
    true,
    10000,
    10000000,
    0,
    0,
    24,
    '7152',
    1
);
