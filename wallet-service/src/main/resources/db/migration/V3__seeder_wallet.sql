INSERT INTO wallet_oltp.wallets (id, user_id, currency, status, balance_snapshot, created_at, updated_at)
VALUES
  ('aaaaaaa1-aaaa-aaaa-aaaa-aaaaaaaaaaa1', '11111111-1111-1111-1111-111111111111', 'IDR', 'ACTIVE',    5000000.00, now(), now()),
  ('bbbbbbb2-bbbb-bbbb-bbbb-bbbbbbbbbbb2', '22222222-2222-2222-2222-222222222222', 'IDR', 'SUSPENDED',  250000.00, now(), now())
ON CONFLICT (id) DO UPDATE
SET user_id          = EXCLUDED.user_id,
    currency         = EXCLUDED.currency,
    status           = EXCLUDED.status,
    balance_snapshot = EXCLUDED.balance_snapshot,
    updated_at       = now();

INSERT INTO wallet_read.wallets (id, user_id, currency, status, balance_snapshot, created_at, updated_at)
SELECT id, user_id, currency, status, balance_snapshot, created_at, updated_at
FROM wallet_oltp.wallets
ON CONFLICT (id) DO UPDATE
SET user_id          = EXCLUDED.user_id,
    currency         = EXCLUDED.currency,
    status           = EXCLUDED.status,
    balance_snapshot = EXCLUDED.balance_snapshot,
    created_at       = EXCLUDED.created_at,
    updated_at       = EXCLUDED.updated_at;
