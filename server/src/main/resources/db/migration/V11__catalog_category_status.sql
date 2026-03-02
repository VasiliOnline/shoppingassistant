-- Persist Category.status in catalog taxonomy table.
ALTER TABLE categories
    ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';

UPDATE categories
SET status = 'ACTIVE'
WHERE status IS NULL OR btrim(status) = '';

