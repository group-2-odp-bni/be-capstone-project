CREATE TABLE IF NOT EXISTS wallet_read.wallets (
  id               UUID PRIMARY KEY,
  user_id          UUID                  NOT NULL, -- creator/legacy owner
  currency         VARCHAR               NOT NULL DEFAULT 'IDR',
  status           domain.wallet_status  NOT NULL DEFAULT 'ACTIVE',
  balance_snapshot NUMERIC(20,2)         NOT NULL DEFAULT 0,
  type             domain.wallet_type    NOT NULL DEFAULT 'PERSONAL',
  name             VARCHAR(160),
  members_active   INT                   NOT NULL DEFAULT 0,
  is_default_for_user BOOLEAN            NOT NULL DEFAULT FALSE,
  created_at       TIMESTAMPTZ           NOT NULL,
  updated_at       TIMESTAMPTZ           NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_wallet_read_user    ON wallet_read.wallets (user_id);
CREATE INDEX IF NOT EXISTS idx_wallet_read_status  ON wallet_read.wallets (status);
CREATE INDEX IF NOT EXISTS idx_wallet_read_type    ON wallet_read.wallets (type);

CREATE TABLE IF NOT EXISTS wallet_read.wallet_members (
  wallet_id        UUID NOT NULL,
  user_id          UUID NOT NULL,
  role             domain.wallet_member_role   NOT NULL,
  status           domain.wallet_member_status NOT NULL,
  daily_limit_rp   BIGINT NOT NULL DEFAULT 0,
  monthly_limit_rp BIGINT NOT NULL DEFAULT 0,
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (wallet_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_wr_wallet_members_wallet ON wallet_read.wallet_members (wallet_id);
CREATE INDEX IF NOT EXISTS idx_wr_wallet_members_user   ON wallet_read.wallet_members (user_id);

CREATE TABLE IF NOT EXISTS wallet_read.user_wallets (
  user_id        UUID NOT NULL,
  wallet_id      UUID NOT NULL,
  is_owner       BOOLEAN NOT NULL,
  wallet_type    domain.wallet_type   NOT NULL,
  wallet_status  domain.wallet_status NOT NULL,
  wallet_name    VARCHAR(160),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, wallet_id)
);

CREATE INDEX IF NOT EXISTS idx_wr_user_wallets_user   ON wallet_read.user_wallets (user_id);
CREATE INDEX IF NOT EXISTS idx_wr_user_wallets_owner  ON wallet_read.user_wallets (user_id, is_owner) WHERE is_owner = TRUE;

CREATE TABLE IF NOT EXISTS wallet_read.alias_directory (
  alias_type   domain.alias_type NOT NULL,
  alias_value  VARCHAR(120)      NOT NULL,
  user_id      UUID              NOT NULL,
  wallet_id    UUID              NOT NULL,
  status       domain.route_status NOT NULL,
  updated_at   TIMESTAMPTZ       NOT NULL DEFAULT now(),
  PRIMARY KEY (alias_type, alias_value)
);

CREATE INDEX IF NOT EXISTS idx_alias_dir_wallet ON wallet_read.alias_directory (wallet_id);
