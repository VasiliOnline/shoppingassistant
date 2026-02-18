CREATE TABLE IF NOT EXISTS track_top10_snapshots (
    track_id BIGINT PRIMARY KEY REFERENCES tracks(id) ON DELETE CASCADE,
    computed_at BIGINT NOT NULL,
    items_json JSONB NOT NULL,
    explanation_json JSONB NOT NULL,
    source_stamps_json JSONB NOT NULL,
    freshness_sec INT NOT NULL,
    locked_by VARCHAR(64) NULL,
    lock_until BIGINT NULL,
    last_attempt_at BIGINT NULL,
    last_success_at BIGINT NULL,
    fail_count INT NOT NULL DEFAULT 0,
    next_retry_at BIGINT NULL,
    last_error VARCHAR(512) NULL
);

CREATE INDEX IF NOT EXISTS idx_track_top10_snapshots_next_retry_at
    ON track_top10_snapshots (next_retry_at);

CREATE INDEX IF NOT EXISTS idx_track_top10_snapshots_lock_until
    ON track_top10_snapshots (lock_until);

CREATE INDEX IF NOT EXISTS idx_track_top10_snapshots_computed_at
    ON track_top10_snapshots (computed_at);
