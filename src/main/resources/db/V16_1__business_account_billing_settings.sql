-- V16_1: Business account billing settings
-- Adds billing/payout columns to business_accounts.
-- Uses IF NOT EXISTS to be idempotent.
ALTER TABLE business_accounts
    ADD COLUMN IF NOT EXISTS gstin VARCHAR(30),
    ADD COLUMN IF NOT EXISTS tax_percent NUMERIC(5, 2) NOT NULL DEFAULT 18.00,
    ADD COLUMN IF NOT EXISTS invoice_prefix VARCHAR(20) NOT NULL DEFAULT 'QL',
    ADD COLUMN IF NOT EXISTS razorpay_key_id VARCHAR(150),
    ADD COLUMN IF NOT EXISTS stripe_publishable_key VARCHAR(200),
    ADD COLUMN IF NOT EXISTS payout_account_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS payout_account_number_masked VARCHAR(40),
    ADD COLUMN IF NOT EXISTS payout_ifsc VARCHAR(20),
    ADD COLUMN IF NOT EXISTS sms_sender_id VARCHAR(20),
    ADD COLUMN IF NOT EXISTS whatsapp_number VARCHAR(20);
