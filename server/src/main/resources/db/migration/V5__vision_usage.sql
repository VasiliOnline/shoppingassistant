CREATE TABLE IF NOT EXISTS vision_usage (
    user_key VARCHAR(128) PRIMARY KEY,
    remaining_today INT NOT NULL,
    remaining_total INT NOT NULL,
    reset_at_millis BIGINT NOT NULL,
    updated_at_millis BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_vision_usage_updated_at
    ON vision_usage (updated_at_millis);
