-- === WRITE MODEL: wallet_oltp.wallet_members =================================

ALTER TABLE wallet_oltp.wallet_members
  ADD COLUMN IF NOT EXISTS alias           VARCHAR(160),
  ADD COLUMN IF NOT EXISTS invited_by      UUID,
  ADD COLUMN IF NOT EXISTS invited_at      TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS joined_at       TIMESTAMPTZ;

ALTER TABLE wallet_oltp.wallet_members
  ADD COLUMN IF NOT EXISTS per_tx_limit_rp BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS weekly_limit_rp BIGINT NOT NULL DEFAULT 0;

-- Constraints (tanpa IF NOT EXISTS) → guard pakai DO $$ ... $$

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint c
    JOIN pg_class t ON c.conrelid = t.oid
    JOIN pg_namespace n ON t.relnamespace = n.oid
    WHERE c.conname = 'chk_members_daily_limit_nonneg'
      AND n.nspname = 'wallet_oltp'
      AND t.relname = 'wallet_members'
  ) THEN
    ALTER TABLE wallet_oltp.wallet_members
      ADD CONSTRAINT chk_members_daily_limit_nonneg CHECK (daily_limit_rp >= 0);
  END IF;
END$$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint c
    JOIN pg_class t ON c.conrelid = t.oid
    JOIN pg_namespace n ON t.relnamespace = n.oid
    WHERE c.conname = 'chk_members_monthly_limit_nonneg'
      AND n.nspname = 'wallet_oltp'
      AND t.relname = 'wallet_members'
  ) THEN
    ALTER TABLE wallet_oltp.wallet_members
      ADD CONSTRAINT chk_members_monthly_limit_nonneg CHECK (monthly_limit_rp >= 0);
  END IF;
END$$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint c
    JOIN pg_class t ON c.conrelid = t.oid
    JOIN pg_namespace n ON t.relnamespace = n.oid
    WHERE c.conname = 'chk_members_weekly_limit_nonneg'
      AND n.nspname = 'wallet_oltp'
      AND t.relname = 'wallet_members'
  ) THEN
    ALTER TABLE wallet_oltp.wallet_members
      ADD CONSTRAINT chk_members_weekly_limit_nonneg CHECK (weekly_limit_rp >= 0);
  END IF;
END$$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint c
    JOIN pg_class t ON c.conrelid = t.oid
    JOIN pg_namespace n ON t.relnamespace = n.oid
    WHERE c.conname = 'chk_members_per_tx_limit_nonneg'
      AND n.nspname = 'wallet_oltp'
      AND t.relname = 'wallet_members'
  ) THEN
    ALTER TABLE wallet_oltp.wallet_members
      ADD CONSTRAINT chk_members_per_tx_limit_nonneg CHECK (per_tx_limit_rp >= 0);
  END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_wallet_members_wallet_id ON wallet_oltp.wallet_members(wallet_id);
CREATE INDEX IF NOT EXISTS idx_wallet_members_user_id   ON wallet_oltp.wallet_members(user_id);

-- === READ MODEL: wallet_read.wallet_members ==================================

ALTER TABLE wallet_read.wallet_members
  ADD COLUMN IF NOT EXISTS alias           VARCHAR(160),
  ADD COLUMN IF NOT EXISTS joined_at       TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS per_tx_limit_rp BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS weekly_limit_rp BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS limit_currency  VARCHAR(3) NOT NULL DEFAULT 'IDR';

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint c
    JOIN pg_class t ON c.conrelid = t.oid
    JOIN pg_namespace n ON t.relnamespace = n.oid
    WHERE c.conname = 'chk_read_members_daily_limit_nonneg'
      AND n.nspname = 'wallet_read'
      AND t.relname = 'wallet_members'
  ) THEN
    ALTER TABLE wallet_read.wallet_members
      ADD CONSTRAINT chk_read_members_daily_limit_nonneg CHECK (daily_limit_rp >= 0);
  END IF;
END$$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint c
    JOIN pg_class t ON c.conrelid = t.oid
    JOIN pg_namespace n ON t.relnamespace = n.oid
    WHERE c.conname = 'chk_read_members_monthly_limit_nonneg'
      AND n.nspname = 'wallet_read'
      AND t.relname = 'wallet_members'
  ) THEN
    ALTER TABLE wallet_read.wallet_members
      ADD CONSTRAINT chk_read_members_monthly_limit_nonneg CHECK (monthly_limit_rp >= 0);
  END IF;
END$$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint c
    JOIN pg_class t ON c.conrelid = t.oid
    JOIN pg_namespace n ON t.relnamespace = n.oid
    WHERE c.conname = 'chk_read_members_weekly_limit_nonneg'
      AND n.nspname = 'wallet_read'
      AND t.relname = 'wallet_members'
  ) THEN
    ALTER TABLE wallet_read.wallet_members
      ADD CONSTRAINT chk_read_members_weekly_limit_nonneg CHECK (weekly_limit_rp >= 0);
  END IF;
END$$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint c
    JOIN pg_class t ON c.conrelid = t.oid
    JOIN pg_namespace n ON t.relnamespace = n.oid
    WHERE c.conname = 'chk_read_members_per_tx_limit_nonneg'
      AND n.nspname = 'wallet_read'
      AND t.relname = 'wallet_members'
  ) THEN
    ALTER TABLE wallet_read.wallet_members
      ADD CONSTRAINT chk_read_members_per_tx_limit_nonneg CHECK (per_tx_limit_rp >= 0);
  END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_read_members_wallet ON wallet_read.wallet_members(wallet_id);
