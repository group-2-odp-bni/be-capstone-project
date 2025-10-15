INSERT INTO auth_oltp.users (id, phone_number, name, pin_hash, status, email_verified, phone_verified, last_login_at)
VALUES
  ('11111111-1111-1111-1111-111111111111', '+6281211111111', 'Steven Active',   'bcrypt:$2b$12$alice', 'ACTIVE', TRUE,  TRUE,  now() - interval '1 day'),
  ('22222222-2222-2222-2222-222222222222', '+6281222222222', 'Siahaan Suspended',  'bcrypt:$2b$12$bobxx', 'SUSPENDED', FALSE, TRUE,  NULL)
ON CONFLICT (id) DO UPDATE
SET phone_number = EXCLUDED.phone_number,
    name         = EXCLUDED.name,
    pin_hash     = EXCLUDED.pin_hash,
    status       = EXCLUDED.status,
    email_verified = EXCLUDED.email_verified,
    phone_verified = EXCLUDED.phone_verified,
    last_login_at  = EXCLUDED.last_login_at;

INSERT INTO auth_oltp.refresh_tokens (id, user_id, token_hash, expiry_date, ip_address, user_agent, is_revoked)
VALUES
  (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'sha256:Steven-refresh-001', now() + interval '7 days', '203.0.113.10', 'Chrome/141 Win64', FALSE),
  (gen_random_uuid(), '22222222-2222-2222-2222-222222222222', 'sha256:Siahaan-refresh-001',   now() + interval '7 days',  '203.0.113.11', 'Chrome/141 Win64', FALSE)
ON CONFLICT (token_hash) DO NOTHING;

INSERT INTO auth_read.user_lookup (id, phone_number, name, status, last_login_at, updated_at)
SELECT id, phone_number, name, status, last_login_at, now()
FROM auth_oltp.users
ON CONFLICT (id) DO UPDATE
SET phone_number = EXCLUDED.phone_number,
    name         = EXCLUDED.name,
    status       = EXCLUDED.status,
    last_login_at= EXCLUDED.last_login_at,
    updated_at   = now();

DELETE FROM auth_read.user_summary;
INSERT INTO auth_read.user_summary (status, total_users, last_update)
SELECT status, COUNT(*), now()
FROM auth_oltp.users
GROUP BY status;
