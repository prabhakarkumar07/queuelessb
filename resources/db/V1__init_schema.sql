-- QueueLess Database Schema
-- V1__init_schema.sql

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- =====================
-- USERS TABLE
-- =====================
CREATE TABLE users (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    phone        VARCHAR(15) NOT NULL UNIQUE,
    email        VARCHAR(255) UNIQUE,
    name         VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role         VARCHAR(20) NOT NULL CHECK (role IN ('CUSTOMER', 'SHOP_OWNER', 'ADMIN')),
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    fcm_token    VARCHAR(500),
    refresh_token TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- =====================
-- SHOPS TABLE
-- =====================
CREATE TABLE shops (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    category        VARCHAR(50) NOT NULL CHECK (category IN ('CLINIC', 'SALON', 'BANK', 'GOVERNMENT', 'RESTAURANT', 'OTHER')),
    description     TEXT,
    address         VARCHAR(500) NOT NULL,
    city            VARCHAR(100) NOT NULL,
    state           VARCHAR(100) NOT NULL,
    pincode         VARCHAR(10) NOT NULL,
    latitude        DECIMAL(10, 8),
    longitude       DECIMAL(11, 8),
    phone           VARCHAR(15) NOT NULL,
    logo_url        VARCHAR(500),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    queue_paused    BOOLEAN NOT NULL DEFAULT FALSE,
    open_time       TIME NOT NULL DEFAULT '09:00:00',
    close_time      TIME NOT NULL DEFAULT '18:00:00',
    avg_service_mins INTEGER NOT NULL DEFAULT 10,
    max_queue_size  INTEGER NOT NULL DEFAULT 100,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_shops_owner ON shops(owner_id);
CREATE INDEX idx_shops_category ON shops(category);
CREATE INDEX idx_shops_city ON shops(city);
CREATE INDEX idx_shops_location ON shops USING GIST (point(longitude, latitude));
CREATE INDEX idx_shops_active ON shops(is_active);

-- =====================
-- SERVICES TABLE
-- =====================
CREATE TABLE services (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    shop_id       UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    name          VARCHAR(200) NOT NULL,
    description   TEXT,
    duration_mins INTEGER NOT NULL DEFAULT 15,
    price         DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_services_shop ON services(shop_id);
CREATE INDEX idx_services_active ON services(is_active);

-- =====================
-- TOKENS TABLE
-- =====================
CREATE TABLE tokens (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    shop_id         UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    service_id      UUID REFERENCES services(id) ON DELETE SET NULL,
    token_number    INTEGER NOT NULL,
    display_number  VARCHAR(10) NOT NULL,  -- e.g., "A042"
    status          VARCHAR(20) NOT NULL DEFAULT 'WAITING'
                    CHECK (status IN ('WAITING', 'CALLED', 'SERVING', 'SERVED', 'SKIPPED', 'CANCELLED', 'EXPIRED')),
    queue_position  INTEGER,
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    called_at       TIMESTAMPTZ,
    served_at       TIMESTAMPTZ,
    cancelled_at    TIMESTAMPTZ,
    sms_sent        BOOLEAN NOT NULL DEFAULT FALSE,
    notes           TEXT,
    date_issued     DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tokens_shop ON tokens(shop_id);
CREATE INDEX idx_tokens_user ON tokens(user_id);
CREATE INDEX idx_tokens_status ON tokens(status);
CREATE INDEX idx_tokens_shop_date ON tokens(shop_id, date_issued);
CREATE INDEX idx_tokens_shop_status ON tokens(shop_id, status);
CREATE UNIQUE INDEX idx_tokens_shop_number_date ON tokens(shop_id, token_number, date_issued);

-- Daily token counter per shop
CREATE TABLE token_sequences (
    shop_id   UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    date      DATE NOT NULL DEFAULT CURRENT_DATE,
    last_number INTEGER NOT NULL DEFAULT 0,
    prefix    VARCHAR(5) NOT NULL DEFAULT 'A',
    PRIMARY KEY (shop_id, date)
);

-- =====================
-- APPOINTMENTS TABLE
-- =====================
CREATE TABLE appointments (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    shop_id         UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    service_id      UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    token_id        UUID REFERENCES tokens(id) ON DELETE SET NULL,
    scheduled_at    TIMESTAMPTZ NOT NULL,
    duration_mins   INTEGER NOT NULL DEFAULT 30,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'RESCHEDULED')),
    payment_status  VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (payment_status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED')),
    payment_id      VARCHAR(100),
    razorpay_order_id VARCHAR(100),
    amount          DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    notes           TEXT,
    cancelled_at    TIMESTAMPTZ,
    cancel_reason   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_appointments_shop ON appointments(shop_id);
CREATE INDEX idx_appointments_user ON appointments(user_id);
CREATE INDEX idx_appointments_scheduled ON appointments(scheduled_at);
CREATE INDEX idx_appointments_status ON appointments(status);

-- =====================
-- NOTIFICATIONS TABLE
-- =====================
CREATE TABLE notifications (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    shop_id     UUID REFERENCES shops(id) ON DELETE SET NULL,
    token_id    UUID REFERENCES tokens(id) ON DELETE SET NULL,
    type        VARCHAR(30) NOT NULL CHECK (type IN ('SMS', 'PUSH', 'IN_APP')),
    title       VARCHAR(200) NOT NULL,
    message     TEXT NOT NULL,
    is_read     BOOLEAN NOT NULL DEFAULT FALSE,
    sent_at     TIMESTAMPTZ,
    delivered   BOOLEAN,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user ON notifications(user_id);
CREATE INDEX idx_notifications_unread ON notifications(user_id, is_read);
CREATE INDEX idx_notifications_created ON notifications(created_at DESC);

-- =====================
-- AUDIT LOG TABLE
-- =====================
CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID REFERENCES users(id),
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id   UUID,
    old_value   JSONB,
    new_value   JSONB,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user ON audit_logs(user_id);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_created ON audit_logs(created_at DESC);

-- =====================
-- TRIGGERS
-- =====================
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER shops_updated_at BEFORE UPDATE ON shops FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER services_updated_at BEFORE UPDATE ON services FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER tokens_updated_at BEFORE UPDATE ON tokens FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER appointments_updated_at BEFORE UPDATE ON appointments FOR EACH ROW EXECUTE FUNCTION update_updated_at();
