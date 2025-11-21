CREATE OR REPLACE FUNCTION wallet_oltp.fn_owner_wallet_limit(p_user UUID)
RETURNS INTEGER AS $$
DECLARE v_lim INT;
BEGIN
  SELECT wallet_count_limit INTO v_lim
  FROM wallet_oltp.wallet_user_limits WHERE user_id = p_user;
  RETURN COALESCE(v_lim, 15);
END;
$$ LANGUAGE plpgsql STABLE;

CREATE OR REPLACE FUNCTION wallet_oltp.fn_created_wallet_limit(p_user UUID)
RETURNS INTEGER AS $$
DECLARE v_lim INT;
BEGIN
  SELECT created_wallet_limit INTO v_lim
  FROM wallet_oltp.wallet_user_limits WHERE user_id = p_user;
  RETURN COALESCE(v_lim, 50);
END;
$$ LANGUAGE plpgsql STABLE;
