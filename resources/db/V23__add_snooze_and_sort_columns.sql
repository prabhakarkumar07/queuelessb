-- Add missing columns to tokens table for snooze and smart sorting features
ALTER TABLE tokens ADD COLUMN IF NOT EXISTS snooze_count INTEGER DEFAULT 0;
ALTER TABLE tokens ADD COLUMN IF NOT EXISTS sort_penalty INTEGER DEFAULT 0;
