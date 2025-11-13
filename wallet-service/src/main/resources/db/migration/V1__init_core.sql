CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SCHEMA IF NOT EXISTS domain;
CREATE SCHEMA IF NOT EXISTS wallet_oltp;
CREATE SCHEMA IF NOT EXISTS wallet_read;
CREATE SCHEMA IF NOT EXISTS infra;

DO $$ BEGIN
  CREATE TYPE domain.wallet_status AS ENUM ('ACTIVE','SUSPENDED','CLOSED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE domain.wallet_type AS ENUM ('PERSONAL','SHARED','FAMILY');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE domain.wallet_member_role AS ENUM ('OWNER','ADMIN','SPENDER','VIEWER');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE domain.wallet_member_status AS ENUM ('INVITED','ACTIVE','SUSPENDED','REMOVED','LEFT');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE domain.alias_type AS ENUM ('PHONE','HANDLE','EMAIL','QR_STATIC','EXTERNAL_ID');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE domain.route_status AS ENUM ('ACTIVE','INACTIVE','REVOKED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE domain.visibility AS ENUM ('PRIVATE','PUBLIC','SELECTIVE');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE OR REPLACE FUNCTION domain.set_updated_at() RETURNS trigger AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END; $$ LANGUAGE plpgsql;
