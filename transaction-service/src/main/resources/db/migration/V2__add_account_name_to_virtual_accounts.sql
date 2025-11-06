-- Add account_name column to virtual_accounts table
ALTER TABLE transaction_oltp.virtual_accounts
ADD COLUMN account_name VARCHAR(255);
