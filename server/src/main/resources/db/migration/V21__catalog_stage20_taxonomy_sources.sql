-- Server-authoritative Stage 2.0 taxonomy sources.

CREATE TABLE IF NOT EXISTS category_aliases (
    alias VARCHAR(255) NOT NULL,
    category_code VARCHAR(64) NOT NULL REFERENCES categories(code) ON DELETE CASCADE,
    PRIMARY KEY (alias, category_code)
);

CREATE TABLE IF NOT EXISTS browse_nodes (
    browse_code VARCHAR(64) PRIMARY KEY,
    parent_browse_code VARCHAR(64) NULL,
    node_kind VARCHAR(16) NOT NULL,
    title_key VARCHAR(128) NULL,
    title_ru TEXT NOT NULL,
    title_en TEXT NULL,
    target_category_code VARCHAR(64) NULL,
    target_type VARCHAR(32) NULL,
    order_index INT NOT NULL DEFAULT 0,
    availability_scope VARCHAR(32) NOT NULL DEFAULT 'ALL',
    icon_key VARCHAR(128) NULL,
    analytics_key VARCHAR(128) NULL,
    search_keywords_ru JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    notes TEXT NULL
);

CREATE TABLE IF NOT EXISTS alias_entries (
    locale VARCHAR(16) NOT NULL,
    term VARCHAR(255) NOT NULL,
    normalized_term VARCHAR(255) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    target_code VARCHAR(64) NOT NULL,
    weight INT NOT NULL,
    match_kind VARCHAR(16) NOT NULL,
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    source VARCHAR(16) NOT NULL,
    notes TEXT NULL,
    PRIMARY KEY (locale, normalized_term, kind, target_code)
);

CREATE TABLE IF NOT EXISTS google_taxonomy_mappings (
    canonical_code VARCHAR(64) PRIMARY KEY REFERENCES categories(code) ON DELETE CASCADE,
    mapping_type VARCHAR(16) NOT NULL,
    google_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    google_paths JSONB NOT NULL DEFAULT '[]'::jsonb,
    notes TEXT NULL
);

CREATE INDEX IF NOT EXISTS idx_category_aliases_category_code
    ON category_aliases(category_code);

CREATE INDEX IF NOT EXISTS idx_browse_nodes_parent
    ON browse_nodes(parent_browse_code);

CREATE INDEX IF NOT EXISTS idx_alias_entries_locale_term
    ON alias_entries(locale, normalized_term);

