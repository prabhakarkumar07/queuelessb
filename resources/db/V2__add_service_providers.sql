-- V2__add_service_providers.sql
-- Adds SERVICE_PROVIDER role support and service_providers table

-- =====================
-- 1. Extend users.role CHECK constraint to allow SERVICE_PROVIDER
-- =====================
ALTER TABLE users
    DROP CONSTRAINT IF EXISTS users_role_check;

ALTER TABLE users
    ADD CONSTRAINT users_role_check
        CHECK (role IN ('CUSTOMER', 'SHOP_OWNER', 'ADMIN', 'SERVICE_PROVIDER'));

-- =====================
-- 2. SERVICE_PROVIDERS TABLE
-- =====================
CREATE TABLE service_providers (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    shop_id      UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title        VARCHAR(100),
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id)
);

CREATE INDEX idx_providers_shop ON service_providers(shop_id);
CREATE INDEX idx_providers_user ON service_providers(user_id);
CREATE INDEX idx_providers_shop_active ON service_providers(shop_id, is_active);

CREATE TRIGGER service_providers_updated_at
    BEFORE UPDATE ON service_providers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- =====================
-- 3. Add provider_id to tokens
-- =====================
ALTER TABLE tokens
    ADD COLUMN IF NOT EXISTS provider_id UUID REFERENCES service_providers(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_tokens_provider ON tokens(provider_id);

-- =====================
-- 4. Add provider_id to appointments
-- =====================
ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS provider_id UUID REFERENCES service_providers(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_appointments_provider ON appointments(provider_id);
