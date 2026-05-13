-- Category deprecation redirect contract: replacement_code.
ALTER TABLE categories
    ADD COLUMN IF NOT EXISTS replacement_code VARCHAR(64) NULL;

UPDATE categories
SET replacement_code = NULL
WHERE replacement_code IS NOT NULL
  AND btrim(replacement_code) = '';

CREATE INDEX IF NOT EXISTS idx_categories_replacement_code
    ON categories(replacement_code);
