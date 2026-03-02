-- Hardening for preset observability contract:
-- facet_preset_code is mandatory and non-blank across all layers.

-- Precheck (same query as runbook):
-- SELECT COUNT(*)
-- FROM catalog_preset_events
-- WHERE facet_preset_code IS NULL OR BTRIM(facet_preset_code) = '';
DO $$
DECLARE
    invalid_count BIGINT;
BEGIN
    SELECT COUNT(*)
      INTO invalid_count
    FROM catalog_preset_events
    WHERE facet_preset_code IS NULL
       OR BTRIM(facet_preset_code) = '';

    RAISE NOTICE 'catalog_preset_events invalid facet_preset_code rows before cleanup: %', invalid_count;
END
$$;

-- Cleanup historical invalid rows before strict constraints.
DELETE FROM catalog_preset_events
WHERE facet_preset_code IS NULL
   OR BTRIM(facet_preset_code) = '';

-- Normalize stored preset codes for stable joins/reporting.
UPDATE catalog_preset_events
SET facet_preset_code = UPPER(BTRIM(facet_preset_code))
WHERE facet_preset_code <> UPPER(BTRIM(facet_preset_code));

-- Make FK behavior explicit for mandatory facet_preset_code.
ALTER TABLE catalog_preset_events
    DROP CONSTRAINT IF EXISTS catalog_preset_events_facet_preset_code_fkey;

ALTER TABLE catalog_preset_events
    ADD CONSTRAINT catalog_preset_events_facet_preset_code_fkey
    FOREIGN KEY (facet_preset_code)
    REFERENCES facet_presets(preset_code)
    ON DELETE RESTRICT;

-- Enforce non-null + non-blank at schema level.
ALTER TABLE catalog_preset_events
    ALTER COLUMN facet_preset_code SET NOT NULL;

ALTER TABLE catalog_preset_events
    DROP CONSTRAINT IF EXISTS chk_catalog_preset_events_facet_preset_code_not_blank;

ALTER TABLE catalog_preset_events
    ADD CONSTRAINT chk_catalog_preset_events_facet_preset_code_not_blank
    CHECK (BTRIM(facet_preset_code) <> '');
