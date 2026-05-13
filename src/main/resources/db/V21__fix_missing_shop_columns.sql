-- V21: Fix missing shop columns due to migration conflict
-- ======================================================

ALTER TABLE shops ADD COLUMN IF NOT EXISTS incident_status VARCHAR(50);
ALTER TABLE shops ADD COLUMN IF NOT EXISTS incident_message VARCHAR(500);
ALTER TABLE shops ADD COLUMN IF NOT EXISTS stop_tokens_before_closing_mins INTEGER DEFAULT 0;
ALTER TABLE shops ADD COLUMN IF NOT EXISTS max_tokens_per_day INTEGER;
