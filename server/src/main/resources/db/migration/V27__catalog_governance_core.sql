CREATE TABLE IF NOT EXISTS catalog_governance_sources (
    id BIGSERIAL PRIMARY KEY,
    source_code VARCHAR(64) NOT NULL,
    external_ref VARCHAR(255) NULL,
    display_name VARCHAR(255) NOT NULL,
    tier VARCHAR(32) NOT NULL,
    default_locale VARCHAR(16) NULL,
    market_code VARCHAR(16) NULL,
    source_version VARCHAR(64) NULL,
    source_uri TEXT NULL,
    checksum VARCHAR(128) NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    captured_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_sources_code_captured_at
    ON catalog_governance_sources (source_code, captured_at);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_sources_code_external_ref
    ON catalog_governance_sources (source_code, external_ref);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_sources_locale_market
    ON catalog_governance_sources (default_locale, market_code);

CREATE TABLE IF NOT EXISTS catalog_governance_brands (
    code VARCHAR(64) PRIMARY KEY,
    labels JSONB NULL,
    normalized_key VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    primary_category_code VARCHAR(64) NULL REFERENCES categories(code) ON DELETE SET NULL,
    primary_segment VARCHAR(16) NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_catalog_governance_brands_normalized_key
    ON catalog_governance_brands (normalized_key);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_brands_primary_category
    ON catalog_governance_brands (primary_category_code);

CREATE TABLE IF NOT EXISTS catalog_governance_product_families (
    code VARCHAR(64) PRIMARY KEY,
    brand_code VARCHAR(64) NOT NULL REFERENCES catalog_governance_brands(code) ON DELETE CASCADE,
    labels JSONB NULL,
    normalized_key VARCHAR(255) NOT NULL,
    pretty_model_prefix VARCHAR(255) NOT NULL,
    variant_tokens JSONB NOT NULL DEFAULT '[]'::jsonb,
    accessory_blockers JSONB NOT NULL DEFAULT '[]'::jsonb,
    default_category_code VARCHAR(64) NULL REFERENCES categories(code) ON DELETE SET NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_catalog_governance_product_families_brand_normalized
    ON catalog_governance_product_families (brand_code, normalized_key);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_product_families_default_category
    ON catalog_governance_product_families (default_category_code);

CREATE TABLE IF NOT EXISTS catalog_governance_models (
    code VARCHAR(96) PRIMARY KEY,
    brand_code VARCHAR(64) NOT NULL REFERENCES catalog_governance_brands(code) ON DELETE CASCADE,
    family_code VARCHAR(64) NULL REFERENCES catalog_governance_product_families(code) ON DELETE SET NULL,
    labels JSONB NULL,
    normalized_key VARCHAR(255) NOT NULL,
    default_category_code VARCHAR(64) NULL REFERENCES categories(code) ON DELETE SET NULL,
    release_year INTEGER NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_catalog_governance_models_brand_normalized
    ON catalog_governance_models (brand_code, normalized_key);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_models_family
    ON catalog_governance_models (family_code);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_models_default_category
    ON catalog_governance_models (default_category_code);

CREATE TABLE IF NOT EXISTS catalog_governance_value_canon (
    id BIGSERIAL PRIMARY KEY,
    attribute_code VARCHAR(64) NOT NULL REFERENCES attribute_defs(code) ON DELETE CASCADE,
    canonical_code VARCHAR(64) NOT NULL,
    canonical_value VARCHAR(255) NOT NULL,
    labels JSONB NULL,
    canonical_locale VARCHAR(16) NULL,
    normalized_value VARCHAR(255) NOT NULL,
    category_code VARCHAR(64) NULL REFERENCES categories(code) ON DELETE SET NULL,
    brand_code VARCHAR(64) NULL REFERENCES catalog_governance_brands(code) ON DELETE SET NULL,
    family_code VARCHAR(64) NULL REFERENCES catalog_governance_product_families(code) ON DELETE SET NULL,
    model_code VARCHAR(96) NULL REFERENCES catalog_governance_models(code) ON DELETE SET NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_value_canon_attribute_code
    ON catalog_governance_value_canon (attribute_code, canonical_code);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_value_canon_attribute_normalized
    ON catalog_governance_value_canon (attribute_code, normalized_value);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_value_canon_attribute_locale
    ON catalog_governance_value_canon (attribute_code, canonical_locale);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_value_canon_scope
    ON catalog_governance_value_canon (category_code, brand_code, family_code, model_code);

CREATE TABLE IF NOT EXISTS catalog_governance_aliases (
    id BIGSERIAL PRIMARY KEY,
    locale VARCHAR(16) NOT NULL,
    market_code VARCHAR(16) NULL,
    alias_text VARCHAR(255) NOT NULL,
    normalized_alias VARCHAR(255) NOT NULL,
    target_kind VARCHAR(32) NOT NULL,
    target_code VARCHAR(128) NOT NULL,
    attribute_code VARCHAR(64) NULL REFERENCES attribute_defs(code) ON DELETE SET NULL,
    category_code VARCHAR(64) NULL REFERENCES categories(code) ON DELETE SET NULL,
    brand_code VARCHAR(64) NULL REFERENCES catalog_governance_brands(code) ON DELETE SET NULL,
    family_code VARCHAR(64) NULL REFERENCES catalog_governance_product_families(code) ON DELETE SET NULL,
    model_code VARCHAR(96) NULL REFERENCES catalog_governance_models(code) ON DELETE SET NULL,
    source_snapshot_id BIGINT NULL REFERENCES catalog_governance_sources(id) ON DELETE SET NULL,
    confidence DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_aliases_lookup
    ON catalog_governance_aliases (locale, market_code, normalized_alias);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_aliases_target
    ON catalog_governance_aliases (target_kind, target_code);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_aliases_scope
    ON catalog_governance_aliases (attribute_code, category_code, brand_code, family_code, model_code);

CREATE TABLE IF NOT EXISTS catalog_governance_value_observations (
    id BIGSERIAL PRIMARY KEY,
    attribute_code VARCHAR(64) NOT NULL REFERENCES attribute_defs(code) ON DELETE CASCADE,
    locale VARCHAR(16) NULL,
    market_code VARCHAR(16) NULL,
    raw_value VARCHAR(255) NOT NULL,
    normalized_value VARCHAR(255) NOT NULL,
    category_code VARCHAR(64) NULL REFERENCES categories(code) ON DELETE SET NULL,
    brand_code VARCHAR(64) NULL REFERENCES catalog_governance_brands(code) ON DELETE SET NULL,
    family_code VARCHAR(64) NULL REFERENCES catalog_governance_product_families(code) ON DELETE SET NULL,
    model_code VARCHAR(96) NULL REFERENCES catalog_governance_models(code) ON DELETE SET NULL,
    source_snapshot_id BIGINT NULL REFERENCES catalog_governance_sources(id) ON DELETE SET NULL,
    observed_count INTEGER NOT NULL DEFAULT 1,
    sample_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    first_seen_at BIGINT NOT NULL,
    last_seen_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_value_observations_lookup
    ON catalog_governance_value_observations (attribute_code, locale, market_code, normalized_value);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_value_observations_scope
    ON catalog_governance_value_observations (category_code, brand_code, family_code, model_code);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_value_observations_source
    ON catalog_governance_value_observations (source_snapshot_id);

CREATE TABLE IF NOT EXISTS catalog_governance_value_candidates (
    id BIGSERIAL PRIMARY KEY,
    attribute_code VARCHAR(64) NOT NULL REFERENCES attribute_defs(code) ON DELETE CASCADE,
    locale VARCHAR(16) NULL,
    market_code VARCHAR(16) NULL,
    raw_value VARCHAR(255) NOT NULL,
    normalized_value VARCHAR(255) NOT NULL,
    proposed_canonical_code VARCHAR(64) NULL,
    proposed_canonical_value VARCHAR(255) NULL,
    proposed_labels JSONB NULL,
    proposed_canonical_locale VARCHAR(16) NULL,
    category_code VARCHAR(64) NULL REFERENCES categories(code) ON DELETE SET NULL,
    brand_code VARCHAR(64) NULL REFERENCES catalog_governance_brands(code) ON DELETE SET NULL,
    family_code VARCHAR(64) NULL REFERENCES catalog_governance_product_families(code) ON DELETE SET NULL,
    model_code VARCHAR(96) NULL REFERENCES catalog_governance_models(code) ON DELETE SET NULL,
    source_snapshot_id BIGINT NULL REFERENCES catalog_governance_sources(id) ON DELETE SET NULL,
    candidate_status VARCHAR(24) NOT NULL DEFAULT 'NEW',
    auto_confidence DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    evidence_count INTEGER NOT NULL DEFAULT 0,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_value_candidates_status
    ON catalog_governance_value_candidates (attribute_code, candidate_status);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_value_candidates_lookup
    ON catalog_governance_value_candidates (attribute_code, locale, market_code, normalized_value);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_value_candidates_scope
    ON catalog_governance_value_candidates (category_code, brand_code, family_code, model_code);

CREATE TABLE IF NOT EXISTS catalog_governance_decisions (
    id BIGSERIAL PRIMARY KEY,
    entity_kind VARCHAR(32) NOT NULL,
    entity_ref VARCHAR(255) NOT NULL,
    action VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    actor VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_decisions_entity
    ON catalog_governance_decisions (entity_kind, entity_ref);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_decisions_created_at
    ON catalog_governance_decisions (created_at);
