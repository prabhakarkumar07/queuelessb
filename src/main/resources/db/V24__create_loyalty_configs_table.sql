-- Create loyalty_configs table
CREATE TABLE IF NOT EXISTS loyalty_configs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    shop_id UUID NOT NULL UNIQUE REFERENCES shops(id) ON DELETE CASCADE,
    points_per_visit INTEGER NOT NULL DEFAULT 10,
    bronze_threshold INTEGER NOT NULL DEFAULT 50,
    silver_threshold INTEGER NOT NULL DEFAULT 200,
    gold_threshold INTEGER NOT NULL DEFAULT 500,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
