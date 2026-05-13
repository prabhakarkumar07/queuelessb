CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_shops_active_city_category
    ON shops(is_active, city, category);

CREATE INDEX IF NOT EXISTS idx_shops_name_trgm
    ON shops USING gin (LOWER(name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_shops_city_trgm
    ON shops USING gin (LOWER(COALESCE(city, '')) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_shops_lat_lng_active
    ON shops(is_active, latitude, longitude)
    WHERE latitude IS NOT NULL AND longitude IS NOT NULL;

CREATE TABLE IF NOT EXISTS daily_shop_stats (
    shop_id             UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    stat_date           DATE NOT NULL,
    total_tokens        BIGINT NOT NULL DEFAULT 0,
    served_tokens       BIGINT NOT NULL DEFAULT 0,
    skipped_tokens      BIGINT NOT NULL DEFAULT 0,
    cancelled_tokens    BIGINT NOT NULL DEFAULT 0,
    avg_wait_minutes    NUMERIC(8, 2) NOT NULL DEFAULT 0,
    avg_service_minutes NUMERIC(8, 2) NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (shop_id, stat_date)
);

CREATE TABLE IF NOT EXISTS hourly_shop_stats (
    shop_id             UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    stat_date           DATE NOT NULL,
    stat_hour           SMALLINT NOT NULL CHECK (stat_hour >= 0 AND stat_hour <= 23),
    total_tokens        BIGINT NOT NULL DEFAULT 0,
    served_tokens       BIGINT NOT NULL DEFAULT 0,
    skipped_tokens      BIGINT NOT NULL DEFAULT 0,
    cancelled_tokens    BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (shop_id, stat_date, stat_hour)
);

CREATE INDEX IF NOT EXISTS idx_daily_shop_stats_date ON daily_shop_stats(stat_date DESC);
CREATE INDEX IF NOT EXISTS idx_hourly_shop_stats_date ON hourly_shop_stats(stat_date DESC, stat_hour);
