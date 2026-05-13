-- Catalog migration parity post-check for V4 -> V6 -> V9 -> V10 -> V11 -> V12 -> V13 -> V14 -> V15 -> V16 -> V17 -> V18 -> V20 -> V21 -> V22 -> V23.
-- Run against each target environment (staging/prod) after deploy.

-- 1) Show applied migration rows.
SELECT
    installed_rank,
    version,
    description,
    installed_on,
    success
FROM flyway_schema_history
WHERE version IN ('4', '6', '9', '10', '11', '12', '13', '14', '15', '16', '17', '18', '20', '21', '22', '23')
ORDER BY installed_rank;

-- 2) Verify required migration order and completeness.
WITH applied AS (
    SELECT
        version::text AS version,
        installed_rank
    FROM flyway_schema_history
    WHERE success = TRUE
      AND version IN ('4', '6', '9', '10', '11', '12', '13', '14', '15', '16', '17', '18', '20', '21', '22', '23')
)
SELECT
    CASE
        WHEN COUNT(*) = 16
         AND ARRAY_AGG(version ORDER BY installed_rank) = ARRAY['4', '6', '9', '10', '11', '12', '13', '14', '15', '16', '17', '18', '20', '21', '22', '23']
            THEN 'OK'
        ELSE 'FAIL'
    END AS migration_order_status,
    ARRAY_AGG(version ORDER BY installed_rank) AS applied_order
FROM applied;

-- 3) Check required columns introduced by V11/V12/V15/V16/V17/V22/V23.
WITH expected(table_name, column_name) AS (
    VALUES
        ('categories', 'status'),
        ('categories', 'title_ru'),
        ('categories', 'title_en'),
        ('categories', 'replacement_code'),
        ('category_aliases', 'alias'),
        ('browse_nodes', 'browse_code'),
        ('browse_nodes', 'title_en'),
        ('alias_entries', 'locale'),
        ('google_taxonomy_mappings', 'canonical_code'),
        ('attribute_defs', 'required_by'),
        ('catalog_constraints', 'effective_from'),
        ('catalog_constraints', 'effective_to'),
        ('facet_definitions', 'effective_from'),
        ('facet_definitions', 'effective_to'),
        ('facet_definitions', 'title_en'),
        ('facet_presets', 'effective_from'),
        ('facet_presets', 'effective_to'),
        ('facet_presets', 'title_en'),
        ('facet_collections', 'title_en'),
        ('catalog_preset_events', 'idempotency_key'),
        ('catalog_preset_events', 'event_type'),
        ('catalog_preset_events', 'query_session_id'),
        ('catalog_preset_events', 'category_code'),
        ('catalog_preset_events', 'facet_preset_code'),
        ('catalog_preset_events', 'occurred_at'),
        ('catalog_preset_events', 'received_at'),
        ('catalog_preset_events', 'event_date'),
        ('catalog_stage4_contract_meta', 'stage'),
        ('catalog_stage4_immutable_attributes', 'attribute_code'),
        ('catalog_stage4_normalization_rules', 'attribute_code'),
        ('catalog_stage4_dedup_templates', 'entity'),
        ('catalog_stage4_execution_metrics', 'metric_date'),
        ('catalog_stage4_typed_constraints', 'attribute_code')
)
SELECT
    e.table_name,
    e.column_name,
    CASE WHEN c.column_name IS NULL THEN 'MISSING' ELSE 'OK' END AS column_status
FROM expected e
LEFT JOIN information_schema.columns c
    ON c.table_schema = 'public'
   AND c.table_name = e.table_name
   AND c.column_name = e.column_name
ORDER BY e.table_name, e.column_name;

-- 4) Data sanity checks after migrations.
SELECT
    COUNT(*) AS invalid_category_status_count
FROM categories
WHERE status IS NULL OR BTRIM(status) = '';

SELECT
    COUNT(*) AS invalid_category_replacement_self_count
FROM categories
WHERE replacement_code IS NOT NULL
  AND BTRIM(replacement_code) <> ''
  AND replacement_code = code;

SELECT
    COUNT(*) AS invalid_category_replacement_target_missing_count
FROM categories c
WHERE c.replacement_code IS NOT NULL
  AND BTRIM(c.replacement_code) <> ''
  AND NOT EXISTS (
    SELECT 1
    FROM categories t
    WHERE t.code = c.replacement_code
  );

SELECT
    COUNT(*) AS invalid_required_by_format_count
FROM attribute_defs
WHERE required_by IS NOT NULL
  AND required_by !~ '^\d{4}-\d{2}-\d{2}$';

SELECT
    COUNT(*) AS invalid_constraints_effective_window_count
FROM catalog_constraints
WHERE effective_from IS NOT NULL
  AND effective_to IS NOT NULL
  AND effective_from > effective_to;

SELECT
    COUNT(*) AS invalid_facet_definition_effective_window_count
FROM facet_definitions
WHERE effective_from IS NOT NULL
  AND effective_to IS NOT NULL
  AND effective_from > effective_to;

SELECT
    COUNT(*) AS invalid_facet_preset_effective_window_count
FROM facet_presets
WHERE effective_from IS NOT NULL
  AND effective_to IS NOT NULL
  AND effective_from > effective_to;

SELECT
    COUNT(*) AS invalid_preset_event_type_count
FROM catalog_preset_events
WHERE event_type NOT IN ('IMPRESSION', 'CLICK', 'CONVERSION');

SELECT
    COUNT(*) AS invalid_preset_event_required_fields_count
FROM catalog_preset_events
WHERE BTRIM(idempotency_key) = ''
   OR BTRIM(query_session_id) = ''
   OR BTRIM(category_code) = ''
   OR BTRIM(COALESCE(facet_preset_code, '')) = ''
   OR occurred_at <= 0
   OR event_date IS NULL;

SELECT
    COUNT(*) AS invalid_preset_event_position_count
FROM catalog_preset_events
WHERE position IS NOT NULL
  AND position <= 0;

SELECT
    COUNT(*) AS stage4_meta_missing_row_count
FROM (
    SELECT 1
    WHERE NOT EXISTS (
        SELECT 1
        FROM catalog_stage4_contract_meta
        WHERE stage = '4.0'
    )
) t;

SELECT
    CASE
        WHEN EXISTS (
            SELECT 1
            FROM pg_indexes
            WHERE schemaname = 'public'
              AND indexname = 'idx_offers_geo_coalesce_gix'
        ) THEN 'OK'
        ELSE 'MISSING'
    END AS geo_coalesce_index_status;
