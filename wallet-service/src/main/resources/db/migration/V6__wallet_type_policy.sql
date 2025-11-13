CREATE TABLE IF NOT EXISTS wallet_oltp.wallet_type_policy (
  type                      domain.wallet_type PRIMARY KEY,
  max_members               INT     NOT NULL,      
  default_daily_cap         BIGINT  NOT NULL,      
  default_monthly_cap       BIGINT  NOT NULL,      
  allow_external_credit     BOOLEAN NOT NULL DEFAULT TRUE,
  allow_member_debit_roles  JSONB   NOT NULL,      
  updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_wallet_type_policy_u ON wallet_oltp.wallet_type_policy;
CREATE TRIGGER trg_wallet_type_policy_u
BEFORE UPDATE ON wallet_oltp.wallet_type_policy
FOR EACH ROW EXECUTE FUNCTION domain.set_updated_at();


INSERT INTO wallet_oltp.wallet_type_policy
(type,      max_members, default_daily_cap, default_monthly_cap, allow_external_credit, allow_member_debit_roles)
VALUES
('PERSONAL', 1,      0,        0,           TRUE, '["OWNER"]'::jsonb),
('SHARED',  10,      0,        0,           TRUE, '["OWNER","ADMIN","SPENDER"]'::jsonb),
('FAMILY',   6,  200000,  2000000,          TRUE, '["OWNER","ADMIN","SPENDER"]'::jsonb)
ON CONFLICT (type) DO UPDATE
SET max_members              = EXCLUDED.max_members,
    default_daily_cap        = EXCLUDED.default_daily_cap,
    default_monthly_cap      = EXCLUDED.default_monthly_cap,
    allow_external_credit    = EXCLUDED.allow_external_credit,
    allow_member_debit_roles = EXCLUDED.allow_member_debit_roles,
    updated_at               = now();
