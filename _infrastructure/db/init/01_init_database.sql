-- 1. Create Roles (Users) for each service
CREATE ROLE auth_user WITH LOGIN PASSWORD 'auth_secret_change_in_prod';
CREATE ROLE user_user WITH LOGIN PASSWORD 'user_secret_change_in_prod';
CREATE ROLE readonly_user WITH LOGIN PASSWORD 'readonly_secret_change_in_prod';

-- 2. Create Schemas for each service
CREATE SCHEMA IF NOT EXISTS auth_oltp AUTHORIZATION auth_user;
CREATE SCHEMA IF NOT EXISTS user_oltp AUTHORIZATION user_user;
CREATE SCHEMA IF NOT EXISTS read_model AUTHORIZATION readonly_user;

-- 3. Grant Privileges
-- Note: Further privileges will be handled by Flyway on a per-table basis,
-- but the schema ownership is the most critical part for isolation.
GRANT USAGE, CREATE ON SCHEMA auth_oltp TO auth_user;
GRANT USAGE, CREATE ON SCHEMA user_oltp TO user_user;
GRANT USAGE ON SCHEMA read_model TO readonly_user;

-- 4. Enable necessary extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 5. Schema documentation
COMMENT ON SCHEMA auth_oltp IS 'Schema for Authentication & Authorization Service - OLTP';
COMMENT ON SCHEMA user_oltp IS 'Schema for User Profile Service - OLTP';
COMMENT ON SCHEMA read_model IS 'Schema for CQRS Read Models & Analytics';