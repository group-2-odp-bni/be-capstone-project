ALTER TABLE transaction_oltp.transactions
ADD COLUMN split_bill_id VARCHAR(100),
ADD COLUMN split_bill_member_id VARCHAR(100);

CREATE INDEX idx_transactions_split_bill
ON transaction_oltp.transactions(split_bill_id, split_bill_member_id)
WHERE split_bill_id IS NOT NULL;
