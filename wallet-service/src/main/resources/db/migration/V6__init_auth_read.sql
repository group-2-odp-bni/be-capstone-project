CREATE SCHEMA IF NOT EXISTS auth_read;

CREATE TABLE auth_read.user_summary (
  status        domain.user_status PRIMARY KEY,
  total_users   BIGINT        NOT NULL,
  last_update   TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_auth_read_user_summary_last_update
  ON auth_read.user_summary (last_update DESC);

CREATE TABLE auth_read.user_lookup (
  id            UUID PRIMARY KEY,
  phone_number  VARCHAR(32)   NOT NULL,
  name          VARCHAR(200)  NOT NULL,
  status        domain.user_status NOT NULL,
  last_login_at TIMESTAMPTZ,
  updated_at    TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_auth_read_user_lookup_phone  ON auth_read.user_lookup (phone_number);
CREATE INDEX idx_auth_read_user_lookup_status ON auth_read.user_lookup (status);
