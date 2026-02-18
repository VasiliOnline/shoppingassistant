-- Stage 2.2 constraints storage (GLOBAL/CATEGORY/BRAND/MODEL scopes)
CREATE TABLE IF NOT EXISTS catalog_constraints (
    id BIGSERIAL PRIMARY KEY,
    scope VARCHAR(16) NOT NULL,
    category_code VARCHAR(64) NULL REFERENCES categories(code) ON DELETE CASCADE,
    brand VARCHAR(255) NULL,
    model VARCHAR(255) NULL,
    attribute_constraints JSONB NOT NULL DEFAULT '[]'::jsonb,
    compatibility_rules JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_catalog_constraints_scope_cat
    ON catalog_constraints(scope, category_code);

CREATE INDEX IF NOT EXISTS idx_catalog_constraints_brand_model
    ON catalog_constraints(category_code, brand, model);

CREATE UNIQUE INDEX IF NOT EXISTS uq_catalog_constraints_key
    ON catalog_constraints(
        scope,
        COALESCE(category_code, ''),
        COALESCE(lower(brand), ''),
        COALESCE(lower(model), '')
    );
