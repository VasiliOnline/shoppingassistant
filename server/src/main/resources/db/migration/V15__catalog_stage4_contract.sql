-- Stage 4.0 runtime contract storage:
-- immutable attribute schema, normalization rules, dedup templates and source metadata.

CREATE TABLE IF NOT EXISTS catalog_stage4_contract_meta (
    stage VARCHAR(8) PRIMARY KEY,
    schema_version VARCHAR(16) NOT NULL,
    stage22_data_version VARCHAR(32) NOT NULL,
    stage22_schema_version VARCHAR(16) NOT NULL,
    stage22_generated_at VARCHAR(64) NOT NULL,
    stage3_version VARCHAR(16) NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS catalog_stage4_immutable_attributes (
    attribute_code VARCHAR(64) PRIMARY KEY,
    value_type VARCHAR(16) NOT NULL,
    value_set_type VARCHAR(16) NOT NULL,
    unit VARCHAR(32) NULL,
    is_identity BOOLEAN NOT NULL,
    is_facet BOOLEAN NOT NULL,
    normalization VARCHAR(128) NULL,
    dictionary_required BOOLEAN NOT NULL,
    immutable_fingerprint VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS catalog_stage4_normalization_rules (
    attribute_code VARCHAR(64) PRIMARY KEY,
    normalization VARCHAR(128) NOT NULL,
    value_set_type VARCHAR(16) NOT NULL,
    dictionary_backed BOOLEAN NOT NULL,
    accepts_free_text BOOLEAN NOT NULL,
    canonical_source VARCHAR(255) NOT NULL,
    dedup_token_mode VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS catalog_stage4_dedup_templates (
    entity VARCHAR(64) PRIMARY KEY,
    template_expr VARCHAR(255) NOT NULL,
    fields JSONB NOT NULL DEFAULT '[]'::jsonb,
    description TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_catalog_stage4_immutable_value_set_type
    ON catalog_stage4_immutable_attributes(value_set_type);

CREATE INDEX IF NOT EXISTS idx_catalog_stage4_normalization_value_set_type
    ON catalog_stage4_normalization_rules(value_set_type);
