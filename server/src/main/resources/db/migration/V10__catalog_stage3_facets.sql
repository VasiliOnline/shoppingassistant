-- Stage 3.0 facets storage: definitions, presets, collections

CREATE TABLE IF NOT EXISTS facet_definitions (
    facet_key VARCHAR(64) PRIMARY KEY,
    title_ru VARCHAR(255) NOT NULL,
    value_type VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    applies_to_category_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    ui_order INT NOT NULL DEFAULT 0,
    ui_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    ui_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    ui_format VARCHAR(64) NULL
);

CREATE TABLE IF NOT EXISTS facet_presets (
    preset_code VARCHAR(64) PRIMARY KEY,
    category_code VARCHAR(64) NOT NULL REFERENCES categories(code) ON DELETE CASCADE,
    title_ru VARCHAR(255) NOT NULL,
    order_index INT NOT NULL DEFAULT 0,
    rules JSONB NOT NULL DEFAULT '[]'::jsonb,
    notes TEXT NULL
);

CREATE TABLE IF NOT EXISTS facet_collections (
    collection_code VARCHAR(64) PRIMARY KEY,
    category_code VARCHAR(64) NOT NULL REFERENCES categories(code) ON DELETE CASCADE,
    title_ru VARCHAR(255) NOT NULL,
    browse_code VARCHAR(64) NULL,
    preset_code VARCHAR(64) NULL REFERENCES facet_presets(preset_code) ON DELETE SET NULL,
    order_index INT NOT NULL DEFAULT 0,
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    notes TEXT NULL
);

CREATE INDEX IF NOT EXISTS idx_facet_presets_category
    ON facet_presets(category_code);

CREATE INDEX IF NOT EXISTS idx_facet_collections_category
    ON facet_collections(category_code);

CREATE INDEX IF NOT EXISTS idx_facet_collections_browse
    ON facet_collections(browse_code);
