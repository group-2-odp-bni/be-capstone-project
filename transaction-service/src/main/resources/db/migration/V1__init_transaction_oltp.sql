CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SCHEMA IF NOT EXISTS domain;
CREATE SCHEMA IF NOT EXISTS transaction_oltp;

DO
$$
    BEGIN
        CREATE TYPE domain.tx_type AS ENUM ('TRANSFER','TOPUP');
    EXCEPTION
        WHEN duplicate_object THEN NULL;
    END
$$;

DO
$$
    BEGIN
        CREATE TYPE domain.tx_status AS ENUM ('PENDING','PROCESSING','SUCCEEDED','FAILED','EXPIRED');
    EXCEPTION
        WHEN duplicate_object THEN NULL;
    END
$$;

DO
$$
    BEGIN
        CREATE TYPE domain.wallet_status AS ENUM ('ACTIVE','SUSPENDED','CLOSED');
    EXCEPTION
        WHEN duplicate_object THEN NULL;
    END
$$;

CREATE OR REPLACE FUNCTION domain.set_updated_at() RETURNS trigger AS
$$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE transaction_oltp.transactions
(
    id           UUID PRIMARY KEY          DEFAULT gen_random_uuid(),
    wallet_id    UUID             NOT NULL,
    trx_id       VARCHAR          NOT NULL,
    type         domain.tx_type   NOT NULL DEFAULT 'TRANSFER',
    amount       NUMERIC(20, 2)   NOT NULL,
    currency     VARCHAR          NOT NULL DEFAULT 'IDR',
    status       domain.tx_status NOT NULL DEFAULT 'PENDING',
    initiated_by UUID,
    created_at   TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT chk_transaction_amount_positive CHECK (amount > 0)
);

CREATE UNIQUE INDEX uq_transaction_trx_id ON transaction_oltp.transactions (trx_id);
CREATE INDEX idx_transaction_wallet_ts ON transaction_oltp.transactions (wallet_id, created_at DESC);
CREATE INDEX idx_transaction_status_ts ON transaction_oltp.transactions (status, created_at DESC);

CREATE TRIGGER trg_transaction_tx_updated_at
    BEFORE UPDATE
    ON transaction_oltp.transactions
    FOR EACH ROW
EXECUTE FUNCTION domain.set_updated_at();

CREATE
    OR REPLACE FUNCTION transaction_oltp.ensure_wallet_active() RETURNS trigger AS
$$
DECLARE
    w_status domain.wallet_status;
BEGIN
    SELECT status
    INTO w_status
    FROM transaction_read.wallet_status_projection
    WHERE wallet_id = NEW.wallet_id;

    IF
        w_status IS NULL THEN
        RAISE EXCEPTION 'Cannot create transaction: wallet % status unknown (projection missing)', NEW.wallet_id
            USING ERRCODE = 'check_violation';
    END IF;
    IF w_status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'Cannot create transaction: wallet % is %', NEW.wallet_id, w_status
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_transaction_guard_wallet_active ON transaction_oltp.transactions;
CREATE TRIGGER trg_transaction_guard_wallet_active
    BEFORE INSERT
    ON transaction_oltp.transactions
    FOR EACH ROW
EXECUTE FUNCTION transaction_oltp.ensure_wallet_active();