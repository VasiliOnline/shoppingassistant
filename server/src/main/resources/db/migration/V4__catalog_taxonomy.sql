-- Catalog: categories, attribute definitions, bindings, dictionaries
CREATE TABLE IF NOT EXISTS categories (
    code VARCHAR(64) PRIMARY KEY,
    segment VARCHAR(16) NOT NULL,
    title VARCHAR(255) NULL,
    parent_code VARCHAR(64) NULL,
    description TEXT NULL
);

CREATE TABLE IF NOT EXISTS attribute_defs (
    code VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    data_type VARCHAR(32) NOT NULL,
    required_for_search BOOLEAN NOT NULL DEFAULT FALSE,
    required_for_offer BOOLEAN NOT NULL DEFAULT FALSE,
    required_for_express BOOLEAN NOT NULL DEFAULT FALSE,
    facet_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    multi_valued BOOLEAN NOT NULL DEFAULT FALSE,
    value_dict_code VARCHAR(64) NULL
);

CREATE TABLE IF NOT EXISTS category_attributes (
    category_code VARCHAR(64) NOT NULL REFERENCES categories(code) ON DELETE CASCADE,
    attribute_code VARCHAR(64) NOT NULL REFERENCES attribute_defs(code) ON DELETE CASCADE,
    ui_order INT NOT NULL DEFAULT 0,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (category_code, attribute_code)
);

CREATE TABLE IF NOT EXISTS attribute_value_dict (
    attribute_code VARCHAR(64) NOT NULL REFERENCES attribute_defs(code) ON DELETE CASCADE,
    canonical_code VARCHAR(64) NOT NULL,
    canonical_value VARCHAR(255) NOT NULL,
    synonyms JSONB NULL,
    PRIMARY KEY (attribute_code, canonical_code)
);

CREATE INDEX IF NOT EXISTS idx_attribute_value_dict_attr ON attribute_value_dict(attribute_code);
