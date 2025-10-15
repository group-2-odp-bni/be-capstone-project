-- V1: Initial combined schema for user-service
-- This script sets up the complete initial schema, consolidating all previous migration files.

-- Create user_oltp schema for the CQRS write model
CREATE SCHEMA IF NOT EXISTS user_oltp;

-- Table: user_profiles (CQRS Write Model)
-- Stores extended user profile data, including fields for pending verification.
CREATE TABLE user_oltp.user_profiles (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) UNIQUE,
    pending_email VARCHAR(255),
    pending_phone VARCHAR(50),
    email_verified_at TIMESTAMP WITH TIME ZONE,
    phone_verified_at TIMESTAMP WITH TIME ZONE,
    bio TEXT,
    address TEXT,
    date_of_birth DATE,
    profile_image_url TEXT,
    synced_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT chk_email_format CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_pending_email_format CHECK (pending_email IS NULL OR pending_email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_phone_format CHECK (phone_number ~* '^\\+?[1-9]\\d{1,14}$'),
    CONSTRAINT chk_pending_phone_format CHECK (pending_phone IS NULL OR pending_phone ~* '^\\+?[1-9]\\d{1,14}$')
);

COMMENT ON SCHEMA user_oltp IS 'CQRS write model schema for user-service profile management operations. OTP verification is handled by Redis.';
COMMENT ON TABLE user_oltp.user_profiles IS 'Extended user profile data with pending verification fields for email/phone updates.';

-- Indexes for user_profiles table
CREATE INDEX idx_user_profiles_email ON user_oltp.user_profiles(email) WHERE email IS NOT NULL;
CREATE INDEX idx_user_profiles_phone ON user_oltp.user_profiles(phone_number);
CREATE INDEX idx_user_profiles_pending_email ON user_oltp.user_profiles(pending_email) WHERE pending_email IS NOT NULL;
CREATE INDEX idx_user_profiles_pending_phone ON user_oltp.user_profiles(pending_phone) WHERE pending_phone IS NOT NULL;
CREATE INDEX idx_user_profiles_synced_at ON user_oltp.user_profiles(synced_at) WHERE synced_at IS NULL;

-- Function to automatically update the 'updated_at' timestamp
CREATE OR REPLACE FUNCTION user_oltp.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to auto-update 'updated_at' on user_profiles table
CREATE TRIGGER trigger_user_profiles_updated_at
    BEFORE UPDATE ON user_oltp.user_profiles
    FOR EACH ROW
    EXECUTE FUNCTION user_oltp.update_updated_at_column();

-- Function to auto-increment the version for optimistic locking
CREATE OR REPLACE FUNCTION user_oltp.increment_version()
RETURNS TRIGGER AS $$
BEGIN
    NEW.version = OLD.version + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to auto-increment version on user_profiles table
CREATE TRIGGER trigger_user_profiles_version
    BEFORE UPDATE ON user_oltp.user_profiles
    FOR EACH ROW
    EXECUTE FUNCTION user_oltp.increment_version();

-- Materialized View: user_profile_read_model (CQRS Read Model)
-- Provides a fast, read-only view of user data synced from auth_oltp.
CREATE MATERIALIZED VIEW IF NOT EXISTS auth_oltp.user_profile_read_model AS
SELECT u.id,
       u.name,
       u.phone_number,
       u.email,
       u.profile_image_url,
       u.status,
       u.phone_verified,
       u.email_verified,
       u.last_login_at,
       u.created_at,
       u.updated_at
FROM auth_oltp.users u WITH DATA;

COMMENT ON MATERIALIZED VIEW auth_oltp.user_profile_read_model IS 'CQRS read model for user profiles, auto-synced from auth_oltp.users via a trigger.';

-- Indexes for the read model to optimize query performance
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_profile_read_model_id ON auth_oltp.user_profile_read_model (id);
CREATE INDEX IF NOT EXISTS idx_user_profile_read_model_phone ON auth_oltp.user_profile_read_model (phone_number);
CREATE INDEX IF NOT EXISTS idx_user_profile_read_model_email ON auth_oltp.user_profile_read_model (email) WHERE email IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_user_profile_read_model_status ON auth_oltp.user_profile_read_model (status);

-- Function to refresh the materialized view concurrently
CREATE OR REPLACE FUNCTION auth_oltp.refresh_user_profile_read_model()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY auth_oltp.user_profile_read_model;
    RETURN NULL;
END;
$$;

-- Trigger to auto-refresh the view when the source data in auth_oltp.users changes
DROP TRIGGER IF EXISTS trigger_refresh_user_profile_read_model ON auth_oltp.users;
CREATE TRIGGER trigger_refresh_user_profile_read_model
    AFTER INSERT OR UPDATE OR DELETE
    ON auth_oltp.users
    FOR EACH STATEMENT
    EXECUTE FUNCTION auth_oltp.refresh_user_profile_read_model();
