-- Migration V3: Add performed_by_user_id to transaction_ledger
-- This tracks which member of a shared wallet initiated the transaction

ALTER TABLE transaction_oltp.transaction_ledger
    ADD COLUMN performed_by_user_id UUID;

CREATE INDEX idx_ledger_performed_by ON transaction_oltp.transaction_ledger(performed_by_user_id);

COMMENT ON COLUMN transaction_oltp.transaction_ledger.performed_by_user_id IS
    'User who initiated the transaction (may differ from user_id in shared wallets). user_id = wallet owner, performed_by_user_id = actual initiator';
