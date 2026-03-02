-- Stage 4 runtime execution metrics storage.
-- Tracks ingest/backfill normalization, drops, logical dedup, unknown attributes.

CREATE TABLE IF NOT EXISTS catalog_stage4_execution_metrics (
    id BIGSERIAL PRIMARY KEY,
    metric_date DATE NOT NULL,
    stream VARCHAR(64) NOT NULL,
    normalized_count INT NOT NULL DEFAULT 0 CHECK (normalized_count >= 0),
    dropped_count INT NOT NULL DEFAULT 0 CHECK (dropped_count >= 0),
    logical_dedup_count INT NOT NULL DEFAULT 0 CHECK (logical_dedup_count >= 0),
    unknown_attribute_count INT NOT NULL DEFAULT 0 CHECK (unknown_attribute_count >= 0),
    reason_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)
);

CREATE INDEX IF NOT EXISTS idx_catalog_stage4_execution_metrics_date_stream
    ON catalog_stage4_execution_metrics(metric_date, stream);

CREATE INDEX IF NOT EXISTS idx_catalog_stage4_execution_metrics_created_at
    ON catalog_stage4_execution_metrics(created_at DESC);

