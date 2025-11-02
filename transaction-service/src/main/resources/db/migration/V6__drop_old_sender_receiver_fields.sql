-- Migration: Drop deprecated sender/receiver fields
-- This migration removes old columns that are no longer needed after dual-record implementation

-- Drop old indexes first
DROP INDEX IF EXISTS transaction_oltp.idx_trx_sender_user;
DROP INDEX IF EXISTS transaction_oltp.idx_trx_receiver_user;
DROP INDEX IF EXISTS transaction_oltp.idx_trx_sender_wallet;
DROP INDEX IF EXISTS transaction_oltp.idx_trx_receiver_wallet;

-- Drop old columns
ALTER TABLE transaction_oltp.transactions
    DROP COLUMN IF EXISTS sender_user_id,
    DROP COLUMN IF EXISTS sender_wallet_id,
    DROP COLUMN IF EXISTS receiver_user_id,
    DROP COLUMN IF EXISTS receiver_wallet_id,
    DROP COLUMN IF EXISTS receiver_name,
    DROP COLUMN IF EXISTS receiver_phone;
