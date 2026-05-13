CREATE TABLE IF NOT EXISTS catalog_governance_source_registry (
    registry_code VARCHAR(96) PRIMARY KEY,
    category_code VARCHAR(64) NOT NULL REFERENCES categories(code) ON DELETE CASCADE,
    connector_type VARCHAR(48) NOT NULL,
    source_code VARCHAR(64) NOT NULL,
    external_ref VARCHAR(255) NULL,
    display_name VARCHAR(255) NOT NULL,
    tier VARCHAR(32) NOT NULL,
    default_locale VARCHAR(16) NULL,
    market_code VARCHAR(16) NULL,
    source_uri TEXT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    auto_publish BOOLEAN NOT NULL DEFAULT TRUE,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_source_registry_category_enabled
    ON catalog_governance_source_registry (category_code, enabled);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_source_registry_source_code
    ON catalog_governance_source_registry (source_code);

CREATE TABLE IF NOT EXISTS catalog_governance_refresh_runs (
    id BIGSERIAL PRIMARY KEY,
    registry_code VARCHAR(96) NOT NULL REFERENCES catalog_governance_source_registry(registry_code) ON DELETE CASCADE,
    category_code VARCHAR(64) NOT NULL REFERENCES categories(code) ON DELETE CASCADE,
    trigger_name VARCHAR(48) NOT NULL,
    status VARCHAR(24) NOT NULL,
    source_snapshot_id BIGINT NULL REFERENCES catalog_governance_sources(id) ON DELETE SET NULL,
    brands_synced INTEGER NOT NULL DEFAULT 0,
    families_synced INTEGER NOT NULL DEFAULT 0,
    models_synced INTEGER NOT NULL DEFAULT 0,
    canonical_values_synced INTEGER NOT NULL DEFAULT 0,
    aliases_synced INTEGER NOT NULL DEFAULT 0,
    candidates_detected INTEGER NOT NULL DEFAULT 0,
    review_queue_size INTEGER NOT NULL DEFAULT 0,
    publish_status VARCHAR(24) NULL,
    error_message TEXT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at BIGINT NOT NULL,
    finished_at BIGINT NULL
);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_refresh_runs_category_started
    ON catalog_governance_refresh_runs (category_code, started_at);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_refresh_runs_registry_started
    ON catalog_governance_refresh_runs (registry_code, started_at);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_refresh_runs_status_started
    ON catalog_governance_refresh_runs (status, started_at);

CREATE TABLE IF NOT EXISTS catalog_governance_publish_events (
    id BIGSERIAL PRIMARY KEY,
    refresh_run_id BIGINT NULL REFERENCES catalog_governance_refresh_runs(id) ON DELETE SET NULL,
    category_code VARCHAR(64) NULL REFERENCES categories(code) ON DELETE SET NULL,
    event_type VARCHAR(48) NOT NULL,
    artifact_type VARCHAR(48) NOT NULL,
    entity_ref VARCHAR(255) NULL,
    status VARCHAR(24) NOT NULL,
    details TEXT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_publish_events_category_created
    ON catalog_governance_publish_events (category_code, created_at);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_publish_events_run_created
    ON catalog_governance_publish_events (refresh_run_id, created_at);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_publish_events_type_created
    ON catalog_governance_publish_events (event_type, created_at);
