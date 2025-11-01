-- Migration: Add dual record support for transactions
-- This migration adds new columns for dual-record transaction pattern
-- where each transfer creates 2 records: one for sender, one for receiver

-- Add new columns for dual record pattern
ALTER TABLE transaction_oltp.transactions
    ADD COLUMN IF NOT EXISTS user_id UUID,
    ADD COLUMN IF NOT EXISTS wallet_id UUID,
    ADD COLUMN IF NOT EXISTS counterparty_user_id UUID,
    ADD COLUMN IF NOT EXISTS counterparty_wallet_id UUID,
    ADD COLUMN IF NOT EXISTS counterparty_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS counterparty_phone VARCHAR(50);

-- Migrate existing data
-- For TRANSFER_OUT transactions
UPDATE transaction_oltp.transactions
SET
    user_id = sender_user_id,
    wallet_id = sender_wallet_id,
    counterparty_user_id = receiver_user_id,
    counterparty_wallet_id = receiver_wallet_id,
    counterparty_name = receiver_name,
    counterparty_phone = receiver_phone
WHERE type = 'TRANSFER_OUT';

-- For TOP_UP transactions (user is the receiver)
UPDATE transaction_oltp.transactions
SET
    user_id = receiver_user_id,
    wallet_id = receiver_wallet_id,
    counterparty_user_id = NULL,
    counterparty_wallet_id = NULL,
    counterparty_name = description,  -- Provider name from description
    counterparty_phone = NULL
WHERE type = 'TOP_UP';

-- Set NOT NULL constraint after data migration
ALTER TABLE transaction_oltp.transactions
    ALTER COLUMN user_id SET NOT NULL,
    ALTER COLUMN wallet_id SET NOT NULL;

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_transactions_user_id
    ON transaction_oltp.transactions(user_id);

CREATE INDEX IF NOT EXISTS idx_transactions_wallet_id
    ON transaction_oltp.transactions(wallet_id);

CREATE INDEX IF NOT EXISTS idx_transactions_user_type
    ON transaction_oltp.transactions(user_id, type);

CREATE INDEX IF NOT EXISTS idx_transactions_user_status
    ON transaction_oltp.transactions(user_id, status);

CREATE INDEX IF NOT EXISTS idx_transactions_user_created
    ON transaction_oltp.transactions(user_id, created_at DESC);

-- Add comment
COMMENT ON COLUMN transaction_oltp.transactions.user_id IS 'Primary user (owner) of this transaction record';
COMMENT ON COLUMN transaction_oltp.transactions.wallet_id IS 'Primary wallet involved in this transaction';
COMMENT ON COLUMN transaction_oltp.transactions.counterparty_user_id IS 'The other party in the transaction (null for top-up)';
COMMENT ON COLUMN transaction_oltp.transactions.counterparty_wallet_id IS 'The other party wallet (null for top-up)';
COMMENT ON COLUMN transaction_oltp.transactions.counterparty_name IS 'Name of counterparty or provider';
COMMENT ON COLUMN transaction_oltp.transactions.counterparty_phone IS 'Phone of counterparty (null for top-up/provider)';
