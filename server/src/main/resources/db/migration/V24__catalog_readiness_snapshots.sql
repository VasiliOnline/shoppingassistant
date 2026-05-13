CREATE TABLE IF NOT EXISTS catalog_readiness_snapshots (
    snapshot_date DATE NOT NULL,
    category_code VARCHAR(64) NOT NULL REFERENCES categories(code) ON DELETE CASCADE,
    readiness VARCHAR(16) NOT NULL,
    editorial_readiness VARCHAR(16) NOT NULL,
    operational_readiness VARCHAR(16) NOT NULL,
    completeness_gate_passed BOOLEAN NOT NULL DEFAULT FALSE,
    blocking_issues JSONB NOT NULL DEFAULT '[]'::jsonb,
    editorial_blocking_issues JSONB NOT NULL DEFAULT '[]'::jsonb,
    operational_blocking_issues JSONB NOT NULL DEFAULT '[]'::jsonb,
    operational_sample_count INTEGER NOT NULL DEFAULT 0,
    operational_dropped_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    operational_unknown_attribute_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    operational_required_missing_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    operational_low_confidence_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    data_version VARCHAR(64) NOT NULL,
    schema_version VARCHAR(16) NOT NULL,
    captured_at BIGINT NOT NULL,
    PRIMARY KEY (snapshot_date, category_code)
);

CREATE INDEX IF NOT EXISTS idx_catalog_readiness_snapshots_category_date
    ON catalog_readiness_snapshots (category_code, snapshot_date);

CREATE INDEX IF NOT EXISTS idx_catalog_readiness_snapshots_data_version_date
    ON catalog_readiness_snapshots (data_version, snapshot_date);

CREATE INDEX IF NOT EXISTS idx_catalog_readiness_snapshots_captured_at
    ON catalog_readiness_snapshots (captured_at);
