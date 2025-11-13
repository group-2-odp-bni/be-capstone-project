CREATE TABLE IF NOT EXISTS wallet_oltp.user_counters (
  user_id                UUID PRIMARY KEY,
  wallet_created_total   INT  NOT NULL DEFAULT 0,
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_user_counters_updated_at ON wallet_oltp.user_counters;
CREATE TRIGGER trg_user_counters_updated_at
BEFORE UPDATE ON wallet_oltp.user_counters
FOR EACH ROW EXECUTE FUNCTION domain.set_updated_at();

CREATE OR REPLACE FUNCTION wallet_oltp.fn_count_owned_wallets(p_user UUID)
RETURNS INTEGER AS $$
DECLARE v_cnt INT;
BEGIN
  SELECT COUNT(DISTINCT m.wallet_id)
    INTO v_cnt
  FROM wallet_oltp.wallet_members m
  JOIN wallet_oltp.wallets w ON w.id = m.wallet_id
  WHERE m.user_id = p_user
    AND m.role = 'OWNER'
    AND m.status IN ('INVITED','ACTIVE')
    AND w.status <> 'CLOSED';
  RETURN COALESCE(v_cnt, 0);
END;
$$ LANGUAGE plpgsql STABLE;

CREATE OR REPLACE FUNCTION wallet_oltp.fn_owner_wallet_limit(p_user UUID)
RETURNS INTEGER AS $$
DECLARE v_lim INT;
BEGIN
  SELECT wallet_count_limit INTO v_lim
  FROM wallet_oltp.wallet_user_limits WHERE user_id = p_user;
  RETURN COALESCE(v_lim, 5);
END; $$ LANGUAGE plpgsql STABLE;

CREATE OR REPLACE FUNCTION wallet_oltp.fn_created_wallet_limit(p_user UUID)
RETURNS INTEGER AS $$
DECLARE v_lim INT;
BEGIN
  SELECT created_wallet_limit INTO v_lim
  FROM wallet_oltp.wallet_user_limits WHERE user_id = p_user;
  RETURN COALESCE(v_lim, 10);
END; $$ LANGUAGE plpgsql STABLE;

CREATE OR REPLACE FUNCTION wallet_oltp.lock_user(p_user UUID)
RETURNS VOID AS $$
BEGIN
  PERFORM pg_advisory_xact_lock( ('x'||substr(replace(p_user::text,'-',''),1,16))::bit(64)::bigint );
END; $$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION wallet_oltp.trg_enforce_on_wallet_insert()
RETURNS trigger AS $$
DECLARE v_created INT; v_created_lim INT;
DECLARE v_owner   INT; v_owner_lim   INT;
BEGIN
  PERFORM wallet_oltp.lock_user(NEW.user_id);

  SELECT wallet_created_total INTO v_created
  FROM wallet_oltp.user_counters WHERE user_id = NEW.user_id;
  v_created := COALESCE(v_created, 0);
  v_created_lim := wallet_oltp.fn_created_wallet_limit(NEW.user_id);
  IF (v_created + 1) > v_created_lim THEN
    RAISE EXCEPTION USING
      ERRCODE = 'P0001',
      MESSAGE = format('CREATED_LIMIT_REACHED: created=%s, limit=%s', v_created, v_created_lim),
      DETAIL  = 'User has reached lifetime created wallet limit.';
  END IF;

  v_owner := wallet_oltp.fn_count_owned_wallets(NEW.user_id);
  v_owner_lim := wallet_oltp.fn_owner_wallet_limit(NEW.user_id);
  IF (v_owner + 1) > v_owner_lim THEN
    RAISE EXCEPTION USING
      ERRCODE = 'P0001',
      MESSAGE = format('OWNER_LIMIT_REACHED: owner=%s, limit=%s', v_owner, v_owner_lim),
      DETAIL  = 'User has reached concurrent owned wallet limit.';
  END IF;

  RETURN NEW;
END; $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_enforce_on_wallet_insert ON wallet_oltp.wallets;
CREATE TRIGGER trg_enforce_on_wallet_insert
BEFORE INSERT ON wallet_oltp.wallets
FOR EACH ROW EXECUTE FUNCTION wallet_oltp.trg_enforce_on_wallet_insert();

CREATE OR REPLACE FUNCTION wallet_oltp.trg_increment_created_counter()
RETURNS trigger AS $$
BEGIN
  INSERT INTO wallet_oltp.user_counters (user_id, wallet_created_total)
  VALUES (NEW.user_id, 1)
  ON CONFLICT (user_id) DO UPDATE
    SET wallet_created_total = wallet_oltp.user_counters.wallet_created_total + 1,
        updated_at = now();
  RETURN NEW;
END; $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_increment_created_counter ON wallet_oltp.wallets;
CREATE TRIGGER trg_increment_created_counter
AFTER INSERT ON wallet_oltp.wallets
FOR EACH ROW EXECUTE FUNCTION wallet_oltp.trg_increment_created_counter();

CREATE OR REPLACE FUNCTION wallet_oltp.trg_enforce_owner_on_membership()
RETURNS trigger AS $$
DECLARE was_owner BOOLEAN; is_owner BOOLEAN;
BEGIN
  PERFORM wallet_oltp.lock_user(COALESCE(NEW.user_id, OLD.user_id));

  was_owner := (OLD.role = 'OWNER' AND OLD.status IN ('INVITED','ACTIVE'));
  is_owner  := (NEW.role = 'OWNER' AND NEW.status IN ('INVITED','ACTIVE'));

  IF (TG_OP = 'INSERT' AND is_owner) OR (TG_OP='UPDATE' AND (NOT was_owner) AND is_owner) THEN
    IF (wallet_oltp.fn_count_owned_wallets(NEW.user_id) + 1) > wallet_oltp.fn_owner_wallet_limit(NEW.user_id) THEN
      RAISE EXCEPTION USING
        ERRCODE = 'P0001',
        MESSAGE = 'OWNER_LIMIT_REACHED',
        DETAIL  = 'User has reached concurrent owned wallet limit.';
    END IF;
  END IF;

  RETURN NEW;
END; $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_enforce_owner_on_membership_ins ON wallet_oltp.wallet_members;
CREATE TRIGGER trg_enforce_owner_on_membership_ins
BEFORE INSERT ON wallet_oltp.wallet_members
FOR EACH ROW EXECUTE FUNCTION wallet_oltp.trg_enforce_owner_on_membership();

DROP TRIGGER IF EXISTS trg_enforce_owner_on_membership_upd ON wallet_oltp.wallet_members;
CREATE TRIGGER trg_enforce_owner_on_membership_upd
BEFORE UPDATE ON wallet_oltp.wallet_members
FOR EACH ROW EXECUTE FUNCTION wallet_oltp.trg_enforce_owner_on_membership();
