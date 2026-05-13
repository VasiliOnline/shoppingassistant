ALTER TABLE user_preferences
    ADD COLUMN IF NOT EXISTS delivery_zones JSONB NULL;
