ALTER TABLE shops
    ADD COLUMN IF NOT EXISTS business_account_id UUID,
    ADD COLUMN IF NOT EXISTS branch_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS no_show_grace_mins INTEGER NOT NULL DEFAULT 5,
    ADD COLUMN IF NOT EXISTS rejoin_window_mins INTEGER NOT NULL DEFAULT 15,
    ADD COLUMN IF NOT EXISTS max_rejoins INTEGER NOT NULL DEFAULT 1;

ALTER TABLE tokens
    ADD COLUMN IF NOT EXISTS skipped_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejoin_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE reviews
    ADD COLUMN IF NOT EXISTS visible BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN IF NOT EXISTS moderation_reason TEXT,
    ADD COLUMN IF NOT EXISTS moderated_at TIMESTAMPTZ;

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS delivery_status VARCHAR(20) NOT NULL DEFAULT 'DELIVERED',
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS provider_message_id VARCHAR(150),
    ADD COLUMN IF NOT EXISTS last_error TEXT,
    ADD COLUMN IF NOT EXISTS channel VARCHAR(20);

CREATE TABLE IF NOT EXISTS business_accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    billing_email VARCHAR(200),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE shops
    ADD CONSTRAINT fk_shops_business_account
    FOREIGN KEY (business_account_id) REFERENCES business_accounts(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_business_accounts_owner ON business_accounts(owner_id);
CREATE INDEX IF NOT EXISTS idx_shops_business_account ON shops(business_account_id);

CREATE TABLE IF NOT EXISTS otp_codes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    phone VARCHAR(15) NOT NULL,
    otp_hash VARCHAR(128) NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    consumed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_otp_codes_phone_purpose ON otp_codes(phone, purpose, expires_at DESC);

CREATE TABLE IF NOT EXISTS shop_subscriptions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    shop_id UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    plan VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    amount NUMERIC(10, 2) NOT NULL DEFAULT 0,
    current_period_start TIMESTAMPTZ NOT NULL,
    current_period_end TIMESTAMPTZ NOT NULL,
    provider_subscription_id VARCHAR(150),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_shop_subscriptions_shop ON shop_subscriptions(shop_id, status);

CREATE TABLE IF NOT EXISTS staff_heartbeats (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    shop_id UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    provider_id UUID NOT NULL REFERENCES service_providers(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id VARCHAR(120) NOT NULL,
    app_version VARCHAR(80),
    online BOOLEAN NOT NULL DEFAULT true,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(provider_id, device_id)
);

CREATE INDEX IF NOT EXISTS idx_staff_heartbeats_shop_last_seen ON staff_heartbeats(shop_id, last_seen_at DESC);
