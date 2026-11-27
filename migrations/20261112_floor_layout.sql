-- Additive. Apply before deploying the member map/admin layout editor.
ALTER TABLE floor_plans ADD COLUMN IF NOT EXISTS layout jsonb;
ALTER TABLE floor_plans ADD COLUMN IF NOT EXISTS illustrative boolean NOT NULL DEFAULT false;
-- Rollback: roll clients back first, then drop these columns only if their saved layouts are no longer needed.
