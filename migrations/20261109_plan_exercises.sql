-- Additive; existing muscle-group goals keep NULL exercise fields.
ALTER TABLE day_plan_items ADD COLUMN IF NOT EXISTS exercise_id BIGINT;
ALTER TABLE day_plan_items ADD COLUMN IF NOT EXISTS exercise_name VARCHAR(255);
ALTER TABLE day_plan_items ADD COLUMN IF NOT EXISTS equipment_type VARCHAR(255);
-- Roll back only after reverting exercise-aware clients/backend:
-- ALTER TABLE day_plan_items DROP COLUMN exercise_id, DROP COLUMN exercise_name, DROP COLUMN equipment_type;
