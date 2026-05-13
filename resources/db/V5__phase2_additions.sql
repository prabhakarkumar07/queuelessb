-- V5: Phase 2 Feature Additions (Loyalty, Attachments, Reminders)

-- Add reminder flags to appointments and tokens
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS reminder_sent BOOLEAN DEFAULT FALSE;
ALTER TABLE tokens ADD COLUMN IF NOT EXISTS reminder_sent BOOLEAN DEFAULT FALSE;

-- Create attachments table
CREATE TABLE IF NOT EXISTS attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_id UUID NOT NULL,
    target_type VARCHAR(20) NOT NULL, -- 'TOKEN' or 'APPOINTMENT'
    file_url TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_attachments_target ON attachments(target_id, target_type);

-- Create user loyalty table
CREATE TABLE IF NOT EXISTS user_loyalty (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    shop_id UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    points INTEGER DEFAULT 0,
    total_visits INTEGER DEFAULT 0,
    tier VARCHAR(20) DEFAULT 'BRONZE', -- 'BRONZE', 'SILVER', 'GOLD'
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, shop_id)
);

CREATE INDEX IF NOT EXISTS idx_user_loyalty_user ON user_loyalty(user_id);
CREATE INDEX IF NOT EXISTS idx_user_loyalty_shop ON user_loyalty(shop_id);
