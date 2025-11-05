-- Period bucket untuk pemakaian limit user
DO $$ BEGIN
  CREATE TYPE domain.period_type AS ENUM ('DAY','WEEK','MONTH');
EXCEPTION
  WHEN duplicate_object THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS wallet_oltp.user_limit_counters (
  user_id        UUID                NOT NULL,
  period_type    domain.period_type  NOT NULL,   -- DAY/WEEK/MONTH
  period_start   TIMESTAMPTZ         NOT NULL,   -- awal bucket (UTC)
  amount_used_rp BIGINT              NOT NULL DEFAULT 0,
  updated_at     TIMESTAMPTZ         NOT NULL DEFAULT now(),
  created_at     TIMESTAMPTZ         NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, period_type, period_start)
);

CREATE INDEX IF NOT EXISTS idx_ulc_user_period ON wallet_oltp.user_limit_counters (user_id, period_type);

DROP TRIGGER IF EXISTS trg_ulc_updated_at ON wallet_oltp.user_limit_counters;
CREATE TRIGGER trg_ulc_updated_at
BEFORE UPDATE ON wallet_oltp.user_limit_counters
FOR EACH ROW EXECUTE FUNCTION domain.set_updated_at();
