CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS shop_discovery_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    shop_id UUID REFERENCES shops(id) ON DELETE CASCADE,
    event_type VARCHAR(30) NOT NULL CHECK (event_type IN ('SEARCH', 'SEARCH_RESULT', 'VIEW', 'NEARBY_RESULT')),
    query TEXT,
    category VARCHAR(50),
    city VARCHAR(100),
    latitude NUMERIC(10, 8),
    longitude NUMERIC(11, 8),
    source VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_shop_discovery_shop_created
    ON shop_discovery_events(shop_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_shop_discovery_type_created
    ON shop_discovery_events(event_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_shop_discovery_category_created
    ON shop_discovery_events(category, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_shop_discovery_query_trgm
    ON shop_discovery_events USING gin (LOWER(COALESCE(query, '')) gin_trgm_ops);
