-- Initial database setup for all services

-- Create extensions that might be used by various services
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Create schemas for each service
CREATE SCHEMA IF NOT EXISTS auth_oltp;
CREATE SCHEMA IF NOT EXISTS user_oltp;
CREATE SCHEMA IF NOT EXISTS wallet_oltp;
CREATE SCHEMA IF NOT EXISTS wallet_read;
CREATE SCHEMA IF NOT EXISTS domain;

COMMENT ON SCHEMA auth_oltp IS 'Schema for the Authentication Service';
COMMENT ON SCHEMA user_oltp IS 'Schema for the User Service';
COMMENT ON SCHEMA wallet_oltp IS 'Schema for the Wallet Service (OLTP)';
COMMENT ON SCHEMA wallet_read IS 'Schema for the Wallet Service (Read Model)';
COMMENT ON SCHEMA domain IS 'Schema for shared domain types and functions';