CREATE TABLE IF NOT EXISTS catalog_phone_model_enrichment_candidates (
    id BIGSERIAL PRIMARY KEY,
    candidate_key VARCHAR(255) NOT NULL,
    category_code VARCHAR(64) NOT NULL REFERENCES categories(code) ON DELETE CASCADE,
    brand_raw VARCHAR(255) NOT NULL,
    brand_normalized VARCHAR(255) NOT NULL,
    brand_code VARCHAR(64) NULL REFERENCES catalog_governance_brands(code) ON DELETE SET NULL,
    family_raw VARCHAR(255) NULL,
    family_code VARCHAR(64) NULL REFERENCES catalog_governance_product_families(code) ON DELETE SET NULL,
    canonical_model_code VARCHAR(96) NULL REFERENCES catalog_governance_models(code) ON DELETE SET NULL,
    model_raw VARCHAR(255) NOT NULL,
    model_normalized VARCHAR(255) NOT NULL,
    official_source_code VARCHAR(64) NULL,
    official_endpoint_code VARCHAR(96) NULL,
    status VARCHAR(40) NOT NULL,
    observed_count INTEGER NOT NULL DEFAULT 0,
    distinct_seller_count INTEGER NOT NULL DEFAULT 0,
    seller_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    sample_offer_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    max_confidence DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    reason_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    first_seen_at BIGINT NOT NULL,
    last_seen_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_catalog_phone_model_enrichment_candidates_key
    ON catalog_phone_model_enrichment_candidates (candidate_key);

CREATE INDEX IF NOT EXISTS idx_catalog_phone_model_enrichment_candidates_category_status
    ON catalog_phone_model_enrichment_candidates (category_code, status);

CREATE INDEX IF NOT EXISTS idx_catalog_phone_model_enrichment_candidates_brand_status
    ON catalog_phone_model_enrichment_candidates (brand_code, status);

CREATE INDEX IF NOT EXISTS idx_catalog_phone_model_enrichment_candidates_model
    ON catalog_phone_model_enrichment_candidates (canonical_model_code);

CREATE INDEX IF NOT EXISTS idx_catalog_phone_model_enrichment_candidates_updated
    ON catalog_phone_model_enrichment_candidates (updated_at);

CREATE TABLE IF NOT EXISTS catalog_governance_official_phone_endpoint_overlays (
    id BIGSERIAL PRIMARY KEY,
    candidate_id BIGINT NULL REFERENCES catalog_phone_model_enrichment_candidates(id) ON DELETE SET NULL,
    category_code VARCHAR(64) NOT NULL REFERENCES categories(code) ON DELETE CASCADE,
    source_code VARCHAR(64) NOT NULL,
    brand_code VARCHAR(64) NOT NULL REFERENCES catalog_governance_brands(code) ON DELETE CASCADE,
    endpoint_code VARCHAR(96) NOT NULL,
    parser_type VARCHAR(64) NOT NULL,
    source_uri TEXT NOT NULL,
    family_code VARCHAR(64) NOT NULL REFERENCES catalog_governance_product_families(code) ON DELETE CASCADE,
    model_code VARCHAR(96) NOT NULL REFERENCES catalog_governance_models(code) ON DELETE CASCADE,
    model_label VARCHAR(255) NOT NULL,
    release_year INTEGER NULL,
    release_date VARCHAR(32) NULL,
    aliases JSONB NOT NULL DEFAULT '{}'::jsonb,
    fixed_values JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(128) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_catalog_governance_official_phone_endpoint_overlays_source_endpoint
    ON catalog_governance_official_phone_endpoint_overlays (category_code, source_code, endpoint_code);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_official_phone_endpoint_overlays_source
    ON catalog_governance_official_phone_endpoint_overlays (category_code, source_code);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_official_phone_endpoint_overlays_brand_model
    ON catalog_governance_official_phone_endpoint_overlays (brand_code, model_code);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_official_phone_endpoint_overlays_candidate
    ON catalog_governance_official_phone_endpoint_overlays (candidate_id);

CREATE INDEX IF NOT EXISTS idx_catalog_governance_official_phone_endpoint_overlays_updated
    ON catalog_governance_official_phone_endpoint_overlays (updated_at);
