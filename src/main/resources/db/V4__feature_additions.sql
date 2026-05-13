-- V4__feature_additions.sql
-- Adds: shop_holidays, priority queue, announcements, reviews, waitlist
-- Fixes: break_start/end + closed_days columns if not already present (from entity mapping)

-- =====================
-- 1. SHOP SCHEDULE FIXES
-- (add columns that exist in Java entity but may be missing from DB)
-- =====================
ALTER TABLE shops ADD COLUMN IF NOT EXISTS break_start_time TIME;
ALTER TABLE shops ADD COLUMN IF NOT EXISTS break_end_time   TIME;

-- =====================
-- 2. SHOP CLOSED DAYS TABLE
-- =====================
CREATE TABLE IF NOT EXISTS shop_closed_days (
    shop_id    UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    day_of_week VARCHAR(20) NOT NULL
        CHECK (day_of_week IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    PRIMARY KEY (shop_id, day_of_week)
);

CREATE INDEX IF NOT EXISTS idx_shop_closed_days_shop ON shop_closed_days(shop_id);

-- =====================
-- 3. SHOP HOLIDAYS TABLE
-- =====================
CREATE TABLE IF NOT EXISTS shop_holidays (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    shop_id    UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    date       DATE NOT NULL,
    reason     VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (shop_id, date)
);

CREATE INDEX IF NOT EXISTS idx_shop_holidays_shop_date ON shop_holidays(shop_id, date);

-- =====================
-- 4. PRIORITY ON TOKENS
-- =====================
ALTER TABLE tokens
    ADD COLUMN IF NOT EXISTS priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL'
        CHECK (priority IN ('NORMAL', 'VIP', 'SENIOR', 'EMERGENCY', 'PREGNANT'));

CREATE INDEX IF NOT EXISTS idx_tokens_priority ON tokens(shop_id, date_issued, priority);

-- =====================
-- 5. ANNOUNCEMENTS TABLE
-- =====================
CREATE TABLE IF NOT EXISTS announcements (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    shop_id    UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    title      VARCHAR(200) NOT NULL,
    message    TEXT NOT NULL,
    type       VARCHAR(20) NOT NULL DEFAULT 'INFO'
                   CHECK (type IN ('INFO', 'WARNING', 'CLOSURE')),
    valid_from TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_to   TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_announcements_shop ON announcements(shop_id);
CREATE INDEX IF NOT EXISTS idx_announcements_active ON announcements(shop_id, valid_from, valid_to);

CREATE OR REPLACE TRIGGER announcements_updated_at
    BEFORE UPDATE ON announcements
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- =====================
-- 6. REVIEWS TABLE
-- =====================
CREATE TABLE IF NOT EXISTS reviews (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    shop_id        UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_id       UUID REFERENCES tokens(id) ON DELETE SET NULL,
    appointment_id UUID REFERENCES appointments(id) ON DELETE SET NULL,
    rating         SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment        TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Prevent duplicate reviews per token or appointment
CREATE UNIQUE INDEX IF NOT EXISTS idx_reviews_user_token       ON reviews(user_id, token_id)       WHERE token_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_reviews_user_appointment ON reviews(user_id, appointment_id) WHERE appointment_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_reviews_shop ON reviews(shop_id);
CREATE INDEX IF NOT EXISTS idx_reviews_user ON reviews(user_id);

-- =====================
-- 7. WAITLIST TABLE
-- =====================
CREATE TABLE IF NOT EXISTS waitlist (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    shop_id      UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    service_id   UUID REFERENCES services(id) ON DELETE SET NULL,
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    notified_at  TIMESTAMPTZ,
    status       VARCHAR(20) NOT NULL DEFAULT 'WAITING'
                     CHECK (status IN ('WAITING', 'NOTIFIED', 'JOINED', 'EXPIRED'))
);

-- A user can only be on the waitlist once per shop at a time
CREATE UNIQUE INDEX IF NOT EXISTS idx_waitlist_active_user_shop ON waitlist(shop_id, user_id)
    WHERE status = 'WAITING';

CREATE INDEX IF NOT EXISTS idx_waitlist_shop_status ON waitlist(shop_id, status, joined_at);
CREATE INDEX IF NOT EXISTS idx_waitlist_user       ON waitlist(user_id);
