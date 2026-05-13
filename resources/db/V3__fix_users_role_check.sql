-- V3__fix_users_role_check.sql
-- Dynamically drops ALL existing check constraints on users.role
-- (handles auto-generated names like users_role_check, users_role_check1, etc.)
-- Then re-adds the correct constraint including SERVICE_PROVIDER.

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'public.users'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%role%'
    LOOP
        EXECUTE format('ALTER TABLE public.users DROP CONSTRAINT %I', r.conname);
        RAISE NOTICE 'Dropped constraint: %', r.conname;
    END LOOP;
END $$;

ALTER TABLE public.users
    ADD CONSTRAINT users_role_check
    CHECK (role IN ('CUSTOMER', 'SHOP_OWNER', 'ADMIN', 'SERVICE_PROVIDER'));
