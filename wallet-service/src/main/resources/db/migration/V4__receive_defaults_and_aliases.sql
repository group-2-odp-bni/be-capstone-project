CREATE TABLE IF NOT EXISTS wallet_oltp.user_receive_prefs (
  user_id           UUID PRIMARY KEY,
  default_wallet_id UUID NOT NULL,
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_user_receive_prefs_u ON wallet_oltp.user_receive_prefs;
CREATE TRIGGER trg_user_receive_prefs_u
BEFORE UPDATE ON wallet_oltp.user_receive_prefs
FOR EACH ROW EXECUTE FUNCTION domain.set_updated_at();

CREATE TABLE IF NOT EXISTS wallet_oltp.wallet_aliases (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID NOT NULL,
  alias_type   domain.alias_type NOT NULL,
  alias_value  VARCHAR(120) NOT NULL,
  wallet_id    UUID NOT NULL,
  status       domain.route_status NOT NULL DEFAULT 'ACTIVE',
  visibility   domain.visibility NOT NULL DEFAULT 'PRIVATE',
  metadata     JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_alias UNIQUE (alias_type, alias_value),
  CONSTRAINT chk_alias_nonempty CHECK (length(alias_value) > 0)
);

DROP TRIGGER IF EXISTS trg_wallet_aliases_u ON wallet_oltp.wallet_aliases;
CREATE TRIGGER trg_wallet_aliases_u
BEFORE UPDATE ON wallet_oltp.wallet_aliases
FOR EACH ROW EXECUTE FUNCTION domain.set_updated_at();

CREATE INDEX IF NOT EXISTS idx_alias_user    ON wallet_oltp.wallet_aliases (user_id);
CREATE INDEX IF NOT EXISTS idx_alias_wallet  ON wallet_oltp.wallet_aliases (wallet_id);
CREATE INDEX IF NOT EXISTS idx_alias_status  ON wallet_oltp.wallet_aliases (status);


CREATE TABLE IF NOT EXISTS wallet_oltp.wallet_alias_audience (
  alias_id       UUID NOT NULL REFERENCES wallet_oltp.wallet_aliases(id) ON DELETE CASCADE,
  viewer_user_id UUID NOT NULL,
  PRIMARY KEY (alias_id, viewer_user_id)
);

CREATE INDEX IF NOT EXISTS idx_alias_audience_alias  ON wallet_oltp.wallet_alias_audience (alias_id);
CREATE INDEX IF NOT EXISTS idx_alias_audience_viewer ON wallet_oltp.wallet_alias_audience (viewer_user_id);
