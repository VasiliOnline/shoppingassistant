-- Preset observability storage for CTR/CVR and controlled A/B governance.

CREATE TABLE IF NOT EXISTS catalog_preset_events (
    id BIGSERIAL NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    query_session_id VARCHAR(128) NOT NULL,
    category_code VARCHAR(64) NOT NULL REFERENCES categories(code) ON DELETE RESTRICT,
    facet_collection_code VARCHAR(64) NULL REFERENCES facet_collections(collection_code) ON DELETE SET NULL,
    facet_preset_code VARCHAR(64) NOT NULL REFERENCES facet_presets(preset_code) ON DELETE RESTRICT,
    offer_id VARCHAR(64) NULL,
    position INT NULL,
    occurred_at BIGINT NOT NULL,
    received_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000),
    event_date DATE NOT NULL,
    data_version VARCHAR(32) NULL,
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    PRIMARY KEY (id, event_date),
    CONSTRAINT chk_catalog_preset_events_type
        CHECK (event_type IN ('IMPRESSION', 'CLICK', 'CONVERSION')),
    CONSTRAINT chk_catalog_preset_events_position
        CHECK (position IS NULL OR position > 0)
) PARTITION BY RANGE (event_date);

CREATE TABLE IF NOT EXISTS catalog_preset_events_default
    PARTITION OF catalog_preset_events DEFAULT;

DO $$
DECLARE
    month_start DATE := DATE_TRUNC('month', CURRENT_DATE)::date;
    month_end DATE := (month_start + INTERVAL '1 month')::date;
    next_month_end DATE := (month_end + INTERVAL '1 month')::date;
    p_current TEXT := FORMAT('catalog_preset_events_%s', TO_CHAR(month_start, 'YYYYMM'));
    p_next TEXT := FORMAT('catalog_preset_events_%s', TO_CHAR(month_end, 'YYYYMM'));
BEGIN
    EXECUTE FORMAT(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF catalog_preset_events FOR VALUES FROM (%L) TO (%L)',
        p_current,
        month_start,
        month_end
    );

    EXECUTE FORMAT(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF catalog_preset_events FOR VALUES FROM (%L) TO (%L)',
        p_next,
        month_end,
        next_month_end
    );
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_catalog_preset_events_event_date_key
    ON catalog_preset_events (event_date, idempotency_key);

CREATE INDEX IF NOT EXISTS idx_catalog_preset_events_event_date
    ON catalog_preset_events (event_date);

CREATE INDEX IF NOT EXISTS idx_catalog_preset_events_preset_daily
    ON catalog_preset_events (event_date, category_code, facet_preset_code, event_type);

CREATE INDEX IF NOT EXISTS idx_catalog_preset_events_session_type_time
    ON catalog_preset_events (query_session_id, event_type, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_catalog_preset_events_received_at
    ON catalog_preset_events (received_at DESC);

CREATE INDEX IF NOT EXISTS idx_catalog_preset_events_data_version
    ON catalog_preset_events (data_version, event_date);

CREATE OR REPLACE FUNCTION purge_catalog_preset_events(retain_days INT DEFAULT 180)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    deleted_rows BIGINT;
BEGIN
    IF retain_days < 30 THEN
        RAISE EXCEPTION 'retain_days must be >= 30';
    END IF;

    DELETE FROM catalog_preset_events
    WHERE event_date < CURRENT_DATE - retain_days;

    GET DIAGNOSTICS deleted_rows = ROW_COUNT;
    RETURN deleted_rows;
END
$$;
