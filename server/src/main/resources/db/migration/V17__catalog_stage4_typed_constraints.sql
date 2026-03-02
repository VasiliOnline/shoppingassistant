-- Stage 4.0 typed runtime constraints and typed-query performance indexes.

CREATE TABLE IF NOT EXISTS catalog_stage4_typed_constraints (
    attribute_code VARCHAR(64) PRIMARY KEY
        REFERENCES catalog_stage4_immutable_attributes(attribute_code)
        ON DELETE CASCADE,
    value_type VARCHAR(16) NOT NULL,
    enum_only BOOLEAN NOT NULL DEFAULT FALSE,
    expected_unit VARCHAR(32) NULL,
    regex_pattern VARCHAR(255) NULL,
    min_value DOUBLE PRECISION NULL,
    max_value DOUBLE PRECISION NULL,
    required_if JSONB NOT NULL DEFAULT '[]'::jsonb,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000),
    CHECK (min_value IS NULL OR max_value IS NULL OR min_value <= max_value)
);

CREATE INDEX IF NOT EXISTS idx_catalog_stage4_typed_constraints_value_type
    ON catalog_stage4_typed_constraints(value_type);

CREATE INDEX IF NOT EXISTS idx_catalog_stage4_typed_constraints_required_if
    ON catalog_stage4_typed_constraints USING GIN (required_if jsonb_path_ops);

-- Typed query acceleration for JSONB containment and common numeric facets.
CREATE INDEX IF NOT EXISTS idx_offers_attributes_path_ops
    ON offers USING GIN (attributes jsonb_path_ops);

CREATE INDEX IF NOT EXISTS idx_products_specs_path_ops
    ON products USING GIN (specs jsonb_path_ops);

CREATE INDEX IF NOT EXISTS idx_products_specs_ram_gb_num
    ON products ((CASE
        WHEN jsonb_typeof(specs->'ram_gb') = 'number' THEN (specs->>'ram_gb')::double precision
        ELSE NULL
    END))
    WHERE specs ? 'ram_gb';

CREATE INDEX IF NOT EXISTS idx_products_specs_storage_gb_num
    ON products ((CASE
        WHEN jsonb_typeof(specs->'storage_gb') = 'number' THEN (specs->>'storage_gb')::double precision
        ELSE NULL
    END))
    WHERE specs ? 'storage_gb';

CREATE INDEX IF NOT EXISTS idx_offers_attributes_ram_gb_num
    ON offers ((CASE
        WHEN jsonb_typeof(attributes->'ram_gb') = 'number' THEN (attributes->>'ram_gb')::double precision
        ELSE NULL
    END))
    WHERE attributes ? 'ram_gb';

CREATE INDEX IF NOT EXISTS idx_offers_attributes_storage_gb_num
    ON offers ((CASE
        WHEN jsonb_typeof(attributes->'storage_gb') = 'number' THEN (attributes->>'storage_gb')::double precision
        ELSE NULL
    END))
    WHERE attributes ? 'storage_gb';

CREATE INDEX IF NOT EXISTS idx_offers_attributes_condition_text
    ON offers ((lower(attributes->>'condition')))
    WHERE attributes ? 'condition';
