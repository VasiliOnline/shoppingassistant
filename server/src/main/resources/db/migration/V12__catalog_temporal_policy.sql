-- Persist temporal policy fields for catalog requirements.

ALTER TABLE IF EXISTS attribute_defs
    ADD COLUMN IF NOT EXISTS required_by VARCHAR(10) NULL;

ALTER TABLE IF EXISTS catalog_constraints
    ADD COLUMN IF NOT EXISTS effective_from VARCHAR(10) NULL,
    ADD COLUMN IF NOT EXISTS effective_to VARCHAR(10) NULL;

ALTER TABLE IF EXISTS facet_definitions
    ADD COLUMN IF NOT EXISTS effective_from VARCHAR(10) NULL,
    ADD COLUMN IF NOT EXISTS effective_to VARCHAR(10) NULL;

ALTER TABLE IF EXISTS facet_presets
    ADD COLUMN IF NOT EXISTS effective_from VARCHAR(10) NULL,
    ADD COLUMN IF NOT EXISTS effective_to VARCHAR(10) NULL;
