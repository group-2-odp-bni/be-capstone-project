DO
$$
BEGIN
        IF
NOT EXISTS (SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = 'auth_oltp'
                          AND table_name = 'refresh_tokens'
                          AND column_name = 'is_revoked') THEN
ALTER TABLE auth_oltp.refresh_tokens
    ADD COLUMN is_revoked BOOLEAN NOT NULL DEFAULT FALSE;
END IF;
        IF
NOT EXISTS (SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = 'auth_oltp'
                          AND table_name = 'refresh_tokens'
                          AND column_name = 'revoked_at') THEN
ALTER TABLE auth_oltp.refresh_tokens
    ADD COLUMN revoked_at TIMESTAMPTZ;
END IF;
END
$$;


CREATE
OR REPLACE FUNCTION auth_oltp.cleanup_old_tokens()
    RETURNS INTEGER AS
$$
DECLARE
deleted_count INTEGER;
BEGIN
DELETE
FROM auth_oltp.refresh_tokens
WHERE is_revoked = TRUE
  AND revoked_at < (now() - INTERVAL '30 days');

GET DIAGNOSTICS deleted_count = ROW_COUNT;
RETURN deleted_count;
END;
$$
LANGUAGE plpgsql;
COMMENT
ON FUNCTION auth_oltp.cleanup_old_tokens() IS 'Permanently remove revoked refresh tokens older than 30 days.';