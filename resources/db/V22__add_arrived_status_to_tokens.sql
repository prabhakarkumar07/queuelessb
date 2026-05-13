-- Drop the old constraint and add a new one that matches the Java TokenStatus enum
ALTER TABLE tokens DROP CONSTRAINT IF EXISTS tokens_status_check;

ALTER TABLE tokens ADD CONSTRAINT tokens_status_check 
CHECK (status IN ('WAITING', 'CALLED', 'ARRIVED', 'SERVING', 'SERVED', 'SKIPPED', 'SNOOZED', 'CANCELLED', 'EXPIRED'));
