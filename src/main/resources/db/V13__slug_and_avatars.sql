-- V13: Add slug to shops + avatar_url to users
-- =============================================

-- 1. Add avatar_url to users
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);

-- 2. Add slug column to shops (nullable first so existing rows can be backfilled)
ALTER TABLE shops ADD COLUMN IF NOT EXISTS slug VARCHAR(150);

-- 3. Backfill slugs for existing shops from their names
--    Pattern: lowercase, replace non-alphanumeric with hyphen, trim leading/trailing hyphens
UPDATE shops
SET slug = regexp_replace(
               regexp_replace(lower(trim(name)), '[^a-z0-9]+', '-', 'g'),
               '^-+|-+$', '', 'g'
           ) || '-' || substr(id::text, 1, 8)
WHERE slug IS NULL;

-- 4. Make slug NOT NULL and UNIQUE
ALTER TABLE shops ALTER COLUMN slug SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_shops_slug ON shops(slug);
