CREATE TABLE IF NOT EXISTS catalog_readiness_automation_leases (
    lease_key VARCHAR(128) PRIMARY KEY,
    owner_id VARCHAR(128) NOT NULL,
    acquired_at BIGINT NOT NULL,
    last_heartbeat_at BIGINT NOT NULL DEFAULT 0,
    lease_expires_at BIGINT NOT NULL
);

ALTER TABLE catalog_readiness_automation_leases
    ADD COLUMN IF NOT EXISTS last_heartbeat_at BIGINT NOT NULL DEFAULT 0;

UPDATE catalog_readiness_automation_leases
SET last_heartbeat_at = acquired_at
WHERE last_heartbeat_at = 0;

CREATE INDEX IF NOT EXISTS idx_catalog_readiness_automation_leases_expires_at
    ON catalog_readiness_automation_leases (lease_expires_at);

CREATE TABLE IF NOT EXISTS catalog_governance_reports (
    report_type VARCHAR(32) NOT NULL,
    report_date DATE NOT NULL,
    generated_at VARCHAR(64) NOT NULL,
    window_start_date DATE NOT NULL,
    window_end_date DATE NOT NULL,
    total_categories INTEGER NOT NULL,
    ready_categories INTEGER NOT NULL,
    beta_categories INTEGER NOT NULL,
    internal_categories INTEGER NOT NULL,
    categories_with_blocking_issues JSONB NOT NULL DEFAULT '[]'::jsonb,
    data_version VARCHAR(64) NOT NULL,
    schema_version VARCHAR(16) NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (report_type, report_date)
);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_reports_date_type
    ON catalog_governance_reports (report_date, report_type);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_reports_created_at
    ON catalog_governance_reports (created_at);

CREATE TABLE IF NOT EXISTS catalog_governance_hook_deliveries (
    id BIGSERIAL PRIMARY KEY,
    hook_code VARCHAR(128) NOT NULL,
    trigger_name VARCHAR(64) NOT NULL,
    transport VARCHAR(32) NOT NULL,
    target TEXT NULL,
    status VARCHAR(64) NOT NULL,
    attempted_at VARCHAR(64) NOT NULL,
    response_status INTEGER NULL,
    details TEXT NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_hook_deliveries_hook
    ON catalog_governance_hook_deliveries (hook_code, trigger_name);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_hook_deliveries_attempted_at
    ON catalog_governance_hook_deliveries (attempted_at);
