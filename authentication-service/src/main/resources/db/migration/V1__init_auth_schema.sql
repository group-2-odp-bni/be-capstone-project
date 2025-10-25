CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TYPE auth_oltp.user_status AS ENUM ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'LOCKED');

CREATE TABLE auth_oltp.users
(
    id                UUID PRIMARY KEY               DEFAULT uuid_generate_v4(),
    phone_number      VARCHAR(32)           NOT NULL UNIQUE,
    name              VARCHAR(200)          NOT NULL,
    user_pins         VARCHAR(255)          NOT NULL,
    status            auth_oltp.user_status NOT NULL DEFAULT 'PENDING_VERIFICATION',
    profile_image_url TEXT,
    email             VARCHAR(255) UNIQUE,
    email_verified    BOOLEAN                        DEFAULT FALSE,
    phone_verified    BOOLEAN                        DEFAULT FALSE,
    last_login_at     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ           NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    CONSTRAINT chk_phone_format CHECK (phone_number ~ '^\+[1-9]\d{1,14}$')
    );

CREATE TABLE auth_oltp.refresh_tokens
(
    id           UUID PRIMARY KEY      DEFAULT uuid_generate_v4(),
    user_id      UUID         NOT NULL REFERENCES auth_oltp.users (id) ON DELETE CASCADE,
    token_hash   VARCHAR(255) NOT NULL UNIQUE,
    expiry_date  TIMESTAMPTZ  NOT NULL,
    ip_address   VARCHAR(45)  NOT NULL,
    user_agent   VARCHAR(256) NOT NULL,
    last_used_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    is_revoked   BOOLEAN               DEFAULT FALSE,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ,
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    CONSTRAINT chk_token_expiry CHECK (expiry_date > now())
);

COMMENT
ON COLUMN auth_oltp.users.email IS 'User email address, unique and optional, used for profile updates and notifications';
COMMENT
ON COLUMN auth_oltp.refresh_tokens.created_at IS 'Timestamp when the refresh token was created';
COMMENT
ON COLUMN auth_oltp.users.created_by IS 'Identifier for the user/system that created the record';
COMMENT
ON COLUMN auth_oltp.users.updated_by IS 'Identifier for the user/system that last updated the record';

CREATE INDEX idx_users_phone_number ON auth_oltp.users (phone_number);
CREATE INDEX idx_users_email ON auth_oltp.users (email) WHERE email IS NOT NULL;
CREATE INDEX idx_refresh_tokens_user_id ON auth_oltp.refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_hash ON auth_oltp.refresh_tokens (token_hash);

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
