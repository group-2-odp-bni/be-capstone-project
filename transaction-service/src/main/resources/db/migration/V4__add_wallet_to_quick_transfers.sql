-- Migration V4: Add wallet_id to quick_transfers
-- Quick transfers should be per-wallet, not per-user

-- Step 1: Add wallet_id column (nullable first)
ALTER TABLE transaction_oltp.quick_transfers
    ADD COLUMN wallet_id UUID;

-- Step 2: Migrate existing data
-- For existing quick transfers, we'll need to set wallet_id
-- Note: This assumes users have their wallets already created
-- In production, you would need to fetch the user's first PERSONAL wallet
-- For now, we leave it NULL and will be populated by application logic

COMMENT ON COLUMN transaction_oltp.quick_transfers.wallet_id IS
    'Source wallet for quick transfer. Quick transfers are now per-wallet to support multi-wallet functionality';

-- Step 3: Create new indexes for wallet-based queries
CREATE INDEX idx_qt_wallet_id ON transaction_oltp.quick_transfers(wallet_id);
CREATE INDEX idx_qt_wallet_usage ON transaction_oltp.quick_transfers(wallet_id, usage_count DESC) WHERE wallet_id IS NOT NULL;
CREATE INDEX idx_qt_wallet_recent ON transaction_oltp.quick_transfers(wallet_id, last_used_at DESC NULLS LAST) WHERE wallet_id IS NOT NULL;

-- Step 4: Drop old unique constraint and create new one
-- We'll do this in a future migration after data migration is complete
-- For now, we keep both constraints to maintain backward compatibility

-- Note: To fully enforce the new constraint, run this after all data is migrated:
-- ALTER TABLE transaction_oltp.quick_transfers DROP CONSTRAINT uq_user_recipient;
-- ALTER TABLE transaction_oltp.quick_transfers ADD CONSTRAINT uq_wallet_recipient UNIQUE (wallet_id, recipient_user_id);
-- ALTER TABLE transaction_oltp.quick_transfers ALTER COLUMN wallet_id SET NOT NULL;
