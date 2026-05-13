-- Add indexes for common queries

-- Tokens table
CREATE INDEX IF NOT EXISTS idx_tokens_shop_id ON tokens(shop_id);
CREATE INDEX IF NOT EXISTS idx_tokens_user_id ON tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_tokens_status ON tokens(status);
CREATE INDEX IF NOT EXISTS idx_tokens_priority ON tokens(priority);
CREATE INDEX IF NOT EXISTS idx_tokens_created_at ON tokens(created_at);

-- Combined index for finding waiting tokens in a shop efficiently
CREATE INDEX IF NOT EXISTS idx_tokens_shop_status ON tokens(shop_id, status);

-- Shops table
CREATE INDEX IF NOT EXISTS idx_shops_owner_id ON shops(owner_id);
CREATE INDEX IF NOT EXISTS idx_shops_category ON shops(category);

-- Users table
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

-- Appointments table
CREATE INDEX IF NOT EXISTS idx_appointments_shop_id ON appointments(shop_id);
CREATE INDEX IF NOT EXISTS idx_appointments_user_id ON appointments(user_id);
CREATE INDEX IF NOT EXISTS idx_appointments_scheduled_at ON appointments(scheduled_at);
CREATE INDEX IF NOT EXISTS idx_appointments_conflict_check ON appointments(shop_id, status, scheduled_at);

-- Subscriptions table
CREATE INDEX IF NOT EXISTS idx_subscriptions_shop_id ON shop_subscriptions(shop_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_status ON shop_subscriptions(status);

-- Reviews
CREATE INDEX IF NOT EXISTS idx_reviews_shop_id ON reviews(shop_id);
