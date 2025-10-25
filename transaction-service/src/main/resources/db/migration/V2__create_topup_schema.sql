DO
$$
BEGIN
    IF
NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'va_status') THEN
CREATE TYPE domain.va_status AS ENUM (
            'ACTIVE',
            'PAID',
            'EXPIRED',
            'CANCELLED'
        );
END IF;
END $$;

DO
$$
BEGIN
    IF
NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'payment_provider') THEN
CREATE TYPE domain.payment_provider AS ENUM (
            'BNI_VA',
            'MANDIRI_VA',
            'BCA_VA',
            'PERMATA_VA'
        );
END IF;
END $$;

CREATE TABLE transaction_oltp.virtual_accounts
(
    id                   UUID PRIMARY KEY            DEFAULT gen_random_uuid(),
    va_number            VARCHAR(20) UNIQUE NOT NULL,
    transaction_id       UUID UNIQUE        NOT NULL,
    user_id              UUID               NOT NULL,
    wallet_id            UUID               NOT NULL,
    provider domain.payment_provider NOT NULL,
    status domain.va_status NOT NULL DEFAULT 'ACTIVE',
    amount               NUMERIC(20, 2)     NOT NULL CHECK (amount > 0),
    paid_amount          NUMERIC(20, 2)              DEFAULT 0 CHECK (paid_amount >= 0),
    expires_at           TIMESTAMPTZ        NOT NULL,
    paid_at              TIMESTAMPTZ,
    expired_at           TIMESTAMPTZ,
    cancelled_at         TIMESTAMPTZ,
    callback_received_at TIMESTAMPTZ,
    callback_payload     JSONB,
    metadata             JSONB,
    created_at           TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_va_transaction FOREIGN KEY (transaction_id)
        REFERENCES transaction_oltp.transactions (id) ON DELETE CASCADE
);

CREATE TABLE transaction_oltp.topup_configs
(
    id              UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    provider domain.payment_provider UNIQUE NOT NULL,
    provider_name   VARCHAR(100)   NOT NULL,
    is_active       BOOLEAN        NOT NULL DEFAULT true,
    min_amount      NUMERIC(20, 2) NOT NULL CHECK (min_amount > 0),
    max_amount      NUMERIC(20, 2) NOT NULL CHECK (max_amount > 0),
    fee_amount      NUMERIC(20, 2) NOT NULL DEFAULT 0 CHECK (fee_amount >= 0),
    fee_percentage  NUMERIC(5, 2)  NOT NULL DEFAULT 0 CHECK (fee_percentage >= 0 AND fee_percentage <= 100),
    va_expiry_hours INTEGER        NOT NULL DEFAULT 24 CHECK (va_expiry_hours > 0),
    va_prefix       VARCHAR(5)     NOT NULL,
    icon_url        TEXT,
    display_order   INTEGER        NOT NULL DEFAULT 0,
    provider_config JSONB,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_min_max CHECK (min_amount < max_amount)
);

CREATE INDEX idx_va_number ON transaction_oltp.virtual_accounts (va_number);
CREATE INDEX idx_va_transaction ON transaction_oltp.virtual_accounts (transaction_id);
CREATE INDEX idx_va_user ON transaction_oltp.virtual_accounts (user_id, created_at DESC);
CREATE INDEX idx_va_status ON transaction_oltp.virtual_accounts (status);
CREATE INDEX idx_va_expires_at ON transaction_oltp.virtual_accounts (expires_at);
CREATE INDEX idx_va_provider_status ON transaction_oltp.virtual_accounts (provider, status);
CREATE INDEX idx_va_created_at ON transaction_oltp.virtual_accounts (created_at DESC);

CREATE INDEX idx_topup_config_provider ON transaction_oltp.topup_configs (provider);
CREATE INDEX idx_topup_config_active ON transaction_oltp.topup_configs (is_active, display_order);

CREATE TRIGGER trg_virtual_accounts_updated_at
    BEFORE UPDATE
    ON transaction_oltp.virtual_accounts
    FOR EACH ROW
    EXECUTE FUNCTION domain.set_updated_at();

CREATE TRIGGER trg_topup_configs_updated_at
    BEFORE UPDATE
    ON transaction_oltp.topup_configs
    FOR EACH ROW
    EXECUTE FUNCTION domain.set_updated_at();

COMMENT
ON TABLE transaction_oltp.virtual_accounts IS 'Virtual Account records for top-up transactions';
COMMENT
ON COLUMN transaction_oltp.virtual_accounts.va_number IS '16-digit VA number generated for payment';
COMMENT
ON COLUMN transaction_oltp.virtual_accounts.transaction_id IS 'Reference to parent transaction record';
COMMENT
ON COLUMN transaction_oltp.virtual_accounts.expires_at IS 'VA expiration time (default 24 hours)';
COMMENT
ON COLUMN transaction_oltp.virtual_accounts.callback_payload IS 'Raw webhook payload from payment provider';
COMMENT
ON COLUMN transaction_oltp.virtual_accounts.metadata IS 'Additional VA metadata';

COMMENT
ON TABLE transaction_oltp.topup_configs IS 'Payment provider configurations for top-up';
COMMENT
ON COLUMN transaction_oltp.topup_configs.provider_config IS 'Provider-specific configuration (API keys, endpoints, etc)';
COMMENT
ON COLUMN transaction_oltp.topup_configs.va_prefix IS 'Prefix for VA number generation (e.g., "7152" for BNI)';
COMMENT
ON COLUMN transaction_oltp.topup_configs.va_expiry_hours IS 'Default VA expiration in hours';

INSERT INTO transaction_oltp.topup_configs (provider, provider_name, is_active, min_amount, max_amount, fee_amount,
                                            fee_percentage, va_expiry_hours, va_prefix, display_order)
VALUES ('BNI_VA', 'BNI Virtual Account', true, 10000, 10000000, 0, 0, 24, '7152', 1);
