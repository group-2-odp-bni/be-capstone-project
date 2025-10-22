CREATE SCHEMA IF NOT EXISTS user_oltp;

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
    sync_status VARCHAR(20) DEFAULT 'SYNCED',
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT chk_email_format CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_pending_email_format CHECK (pending_email IS NULL OR pending_email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_phone_format CHECK (phone_number ~ '^\+[1-9][0-9]{9,14}$'),
    CONSTRAINT chk_pending_phone_format CHECK (pending_phone IS NULL OR pending_phone ~ '^\+[1-9][0-9]{9,14}$'),
    CONSTRAINT chk_sync_status CHECK (sync_status IN ('SYNCED', 'PENDING_SYNC', 'SYNC_FAILED'))
);

COMMENT ON SCHEMA user_oltp IS 'CQRS write model schema for user-service profile management operations. OTP verification is handled by Redis.';
COMMENT ON TABLE user_oltp.user_profiles IS 'Extended user profile data with pending verification fields for email/phone updates.';
COMMENT ON COLUMN user_oltp.user_profiles.sync_status IS 'Tracks synchronization status from authentication-service events. Valid values: SYNCED, PENDING_SYNC, SYNC_FAILED';
COMMENT ON COLUMN user_oltp.user_profiles.synced_at IS 'Timestamp of last successful sync from authentication-service';


CREATE INDEX idx_user_profiles_email ON user_oltp.user_profiles(email) WHERE email IS NOT NULL;
CREATE INDEX idx_user_profiles_phone ON user_oltp.user_profiles(phone_number);
CREATE INDEX idx_user_profiles_pending_email ON user_oltp.user_profiles(pending_email) WHERE pending_email IS NOT NULL;
CREATE INDEX idx_user_profiles_pending_phone ON user_oltp.user_profiles(pending_phone) WHERE pending_phone IS NOT NULL;
CREATE INDEX idx_user_profiles_synced_at ON user_oltp.user_profiles(synced_at) WHERE synced_at IS NULL;
CREATE INDEX idx_user_profiles_sync_failed ON user_oltp.user_profiles(sync_status) WHERE sync_status = 'SYNC_FAILED';
CREATE INDEX idx_user_profiles_sync_pending ON user_oltp.user_profiles(sync_status) WHERE sync_status = 'PENDING_SYNC';
CREATE INDEX idx_user_profiles_created_at ON user_oltp.user_profiles(created_at DESC);


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

-- Function to mark profiles with null synced_at as PENDING_SYNC
CREATE OR REPLACE FUNCTION user_oltp.check_sync_status()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.synced_at IS NULL AND
       NEW.created_at < (NOW() - INTERVAL '5 minutes') AND
       NEW.sync_status = 'SYNCED' THEN
        NEW.sync_status = 'PENDING_SYNC';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION user_oltp.check_sync_status() IS 'Automatically marks profiles as PENDING_SYNC if not synced within 5 minutes';

-- Create trigger to auto-check sync status
CREATE TRIGGER trigger_check_sync_status
    BEFORE UPDATE ON user_oltp.user_profiles
    FOR EACH ROW
    EXECUTE FUNCTION user_oltp.check_sync_status();
