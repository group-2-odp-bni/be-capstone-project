--create shema
CREATE SCHEMA IF NOT EXISTS transfer_oltp;

--define enum for type of transfer
CREATE TYPE tx_type AS ENUM ('TOPUP', 'TRANSFER');
EXCEPTION WHEN duplicate_object THEN NULL;

--define enum for type of transfer status
CREATE TYPE tx_status AS ENUM ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'EXPIRED');
EXCEPTION WHEN duplicate_object THEN NULL;

--create table for transactions
CREATE TABLE IF NOT EXISTS transfer_oltp.transactions (
  id UUID PRIMARY KEY,
  wallet_id UUID NOT NULL,
  trx_id VARCHAR NOT NULL,
  type tx_type NOT NULL,
  amount NUMERIC NOT NULL,
  status tx_status NOT NULL DEFAULT 'PENDING',
  initiated_by UUID,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

--create shema
CREATE SCHEMA IF NOT EXISTS transfer_read;

--create table for transactions
CREATE TABLE IF NOT EXISTS transfer_read.transactions (
  id UUID PRIMARY KEY,
  wallet_id UUID NOT NULL,
  trx_id VARCHAR NOT NULL,
  type tx_type NOT NULL,
  amount NUMERIC NOT NULL,
  status tx_status NOT NULL DEFAULT 'PENDING',
  initiated_by UUID,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
