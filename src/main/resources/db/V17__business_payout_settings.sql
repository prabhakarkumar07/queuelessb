-- V17: Add secret keys and payout status to business accounts
-- =========================================================

ALTER TABLE business_accounts ADD COLUMN IF NOT EXISTS razorpay_key_secret VARCHAR(255);
ALTER TABLE business_accounts ADD COLUMN IF NOT EXISTS payout_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE business_accounts ADD COLUMN IF NOT EXISTS settlement_frequency VARCHAR(50) DEFAULT 'DAILY';
