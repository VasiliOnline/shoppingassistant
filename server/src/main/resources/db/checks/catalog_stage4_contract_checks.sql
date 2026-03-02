-- Stage 4 contract runtime checks.
-- Goal: detect drift between current Stage2/Stage3 DB model and Stage4 contract snapshots.

-- 0) Presence of Stage 4 runtime tables and seed meta.
SELECT
    CASE WHEN to_regclass('public.catalog_stage4_contract_meta') IS NULL THEN 1 ELSE 0 END
        AS missing_stage4_meta_table_count,
    CASE WHEN to_regclass('public.catalog_stage4_immutable_attributes') IS NULL THEN 1 ELSE 0 END
        AS missing_stage4_immutable_table_count,
    CASE WHEN to_regclass('public.catalog_stage4_normalization_rules') IS NULL THEN 1 ELSE 0 END
        AS missing_stage4_normalization_table_count,
    CASE WHEN to_regclass('public.catalog_stage4_dedup_templates') IS NULL THEN 1 ELSE 0 END
        AS missing_stage4_dedup_table_count,
    CASE WHEN to_regclass('public.catalog_stage4_typed_constraints') IS NULL THEN 1 ELSE 0 END
        AS missing_stage4_typed_constraints_table_count;

SELECT
    COUNT(*) AS stage4_meta_rows_count
FROM catalog_stage4_contract_meta;

SELECT
    stage,
    schema_version,
    stage22_data_version,
    stage22_schema_version,
    stage22_generated_at,
    stage3_version,
    updated_at
FROM catalog_stage4_contract_meta
ORDER BY stage;

-- 1) Stage2 attribute defs vs Stage4 immutable schema.
WITH stage2_defs AS (
    SELECT
        ad.code AS attribute_code,
        ad.data_type AS data_type,
        ad.facet_enabled AS is_facet,
        CASE
            WHEN ad.data_type = 'ENUM' THEN 'ENUM'
            WHEN ad.data_type IN ('INT', 'DECIMAL') THEN 'NUMBER'
            WHEN ad.data_type = 'BOOL' THEN 'BOOLEAN'
            ELSE 'STRING'
        END AS expected_value_type,
        EXISTS (
            SELECT 1
            FROM attribute_value_dict d
            WHERE d.attribute_code = ad.code
        ) AS dict_exists
    FROM attribute_defs ad
),
stage4_immutable AS (
    SELECT
        attribute_code,
        value_type,
        value_set_type,
        is_facet,
        dictionary_required
    FROM catalog_stage4_immutable_attributes
)
SELECT
    (SELECT COUNT(*) FROM stage2_defs s2 LEFT JOIN stage4_immutable s4 ON s4.attribute_code = s2.attribute_code WHERE s4.attribute_code IS NULL)
        AS stage4_immutable_missing_from_stage2_count,
    (SELECT COUNT(*) FROM stage4_immutable s4 LEFT JOIN stage2_defs s2 ON s2.attribute_code = s4.attribute_code WHERE s2.attribute_code IS NULL)
        AS stage4_immutable_extra_vs_stage2_count,
    (SELECT COUNT(*) FROM stage2_defs s2 JOIN stage4_immutable s4 ON s4.attribute_code = s2.attribute_code WHERE s4.value_type <> s2.expected_value_type)
        AS stage4_immutable_value_type_mismatch_count,
    (SELECT COUNT(*) FROM stage2_defs s2 JOIN stage4_immutable s4 ON s4.attribute_code = s2.attribute_code
      WHERE s4.dictionary_required <> (s2.expected_value_type = 'ENUM' AND s2.dict_exists))
        AS stage4_immutable_dictionary_required_mismatch_count;

-- 2) Stage4 normalization contract consistency with Stage2/Stage4 immutable.
WITH stage2_defs AS (
    SELECT
        ad.code AS attribute_code,
        CASE
            WHEN ad.data_type = 'ENUM' THEN 'ENUM'
            WHEN ad.data_type IN ('INT', 'DECIMAL') THEN 'NUMBER'
            WHEN ad.data_type = 'BOOL' THEN 'BOOLEAN'
            ELSE 'STRING'
        END AS expected_value_type,
        EXISTS (
            SELECT 1
            FROM attribute_value_dict d
            WHERE d.attribute_code = ad.code
        ) AS dict_exists
    FROM attribute_defs ad
),
stage4_immutable AS (
    SELECT
        attribute_code,
        value_set_type,
        dictionary_required
    FROM catalog_stage4_immutable_attributes
),
stage4_norm AS (
    SELECT
        attribute_code,
        value_set_type,
        dictionary_backed,
        accepts_free_text,
        canonical_source,
        dedup_token_mode
    FROM catalog_stage4_normalization_rules
)
SELECT
    (SELECT COUNT(*) FROM stage2_defs s2 LEFT JOIN stage4_norm n ON n.attribute_code = s2.attribute_code WHERE n.attribute_code IS NULL)
        AS stage4_normalization_missing_from_stage2_count,
    (SELECT COUNT(*) FROM stage4_norm n LEFT JOIN stage2_defs s2 ON s2.attribute_code = n.attribute_code WHERE s2.attribute_code IS NULL)
        AS stage4_normalization_extra_vs_stage2_count,
    (SELECT COUNT(*) FROM stage4_norm n JOIN stage4_immutable i ON i.attribute_code = n.attribute_code
      WHERE n.value_set_type <> i.value_set_type)
        AS stage4_normalization_value_set_mismatch_count,
    (SELECT COUNT(*) FROM stage4_norm n JOIN stage4_immutable i ON i.attribute_code = n.attribute_code
      WHERE n.dictionary_backed <> i.dictionary_required)
        AS stage4_normalization_dictionary_backed_mismatch_count,
    (SELECT COUNT(*) FROM stage4_norm n
      WHERE (n.dictionary_backed AND n.accepts_free_text)
         OR ((NOT n.dictionary_backed) AND (NOT n.accepts_free_text)))
        AS stage4_normalization_accepts_free_text_mismatch_count,
    (SELECT COUNT(*) FROM stage4_norm n
      WHERE (n.dictionary_backed AND (n.canonical_source <> 'taxonomy/stage2/2.2/_registry/value_dictionaries.json' OR n.dedup_token_mode <> 'VALUE_CODE'))
         OR ((NOT n.dictionary_backed) AND (n.canonical_source <> 'inline' OR n.dedup_token_mode <> 'NORMALIZED_TEXT')))
        AS stage4_normalization_canonical_dedup_mode_mismatch_count;

-- 3) Stage3 facet definitions coverage by Stage4 immutable attributes.
WITH stage4_attribute_codes AS (
    SELECT attribute_code
    FROM catalog_stage4_immutable_attributes
),
relevant_stage3_facets AS (
    SELECT fd.facet_key
    FROM facet_definitions fd
    WHERE fd.source <> 'DERIVED'
      AND fd.facet_key <> 'price'
)
SELECT
    COUNT(*) AS stage4_missing_stage3_facet_keys_count
FROM relevant_stage3_facets f
LEFT JOIN stage4_attribute_codes s4
    ON s4.attribute_code = f.facet_key
WHERE s4.attribute_code IS NULL;

-- 4) Stage4 dedup templates completeness.
WITH expected_templates(entity, template_expr, fields_json, description) AS (
    VALUES
        ('ATTRIBUTE_SCHEMA', '{attributeCode}', '["attributeCode"]'::jsonb, 'Stable key for immutable attribute schema entries.'),
        ('DICTIONARY_VALUE', '{attributeCode}|{valueCode}', '["attributeCode","valueCode"]'::jsonb, 'Stable key for dictionary value normalization entries.'),
        ('CATEGORY_PROFILE_ATTRIBUTE', '{categoryCode}|{attributeCode}', '["categoryCode","attributeCode"]'::jsonb, 'Stable key for category profile attribute bindings.'),
        ('FACET_DEFINITION', '{facetKey}', '["facetKey"]'::jsonb, 'Stable key for Stage 3 facet definitions.'),
        ('FACET_PRESET', '{presetCode}', '["presetCode"]'::jsonb, 'Stable key for Stage 3 facet presets.'),
        ('FACET_COLLECTION', '{collectionCode}', '["collectionCode"]'::jsonb, 'Stable key for Stage 3 facet collections.'),
        ('CATEGORY_IDENTITY_SIGNATURE', '{categoryCode}|{identityAttributesHash}', '["categoryCode","identityAttributesHash"]'::jsonb, 'Stable key for per-category identity attribute signatures used in dedup.')
),
actual_templates AS (
    SELECT
        entity,
        template_expr,
        fields,
        description
    FROM catalog_stage4_dedup_templates
)
SELECT
    (SELECT COUNT(*) FROM expected_templates e LEFT JOIN actual_templates a ON a.entity = e.entity WHERE a.entity IS NULL)
        AS stage4_dedup_missing_templates_count,
    (SELECT COUNT(*) FROM actual_templates a LEFT JOIN expected_templates e ON e.entity = a.entity WHERE e.entity IS NULL)
        AS stage4_dedup_extra_templates_count,
    (SELECT COUNT(*) FROM expected_templates e JOIN actual_templates a ON a.entity = e.entity
      WHERE a.template_expr <> e.template_expr OR a.fields <> e.fields_json)
        AS stage4_dedup_template_mismatch_count;

-- 5) Stage 4 typed constraints consistency.
WITH immutable_attrs AS (
    SELECT
        LOWER(attribute_code) AS attribute_code,
        value_type,
        unit
    FROM catalog_stage4_immutable_attributes
),
typed_constraints AS (
    SELECT
        LOWER(attribute_code) AS attribute_code,
        value_type,
        expected_unit,
        min_value,
        max_value,
        required_if
    FROM catalog_stage4_typed_constraints
),
required_if_entries AS (
    SELECT
        tc.attribute_code AS required_attribute_code,
        rule_item ->> 'categoryCode' AS category_code,
        condition_item ->> 'attributeCode' AS condition_attribute_code
    FROM typed_constraints tc
    CROSS JOIN LATERAL JSONB_ARRAY_ELEMENTS(COALESCE(tc.required_if, '[]'::jsonb)) AS rule_item
    CROSS JOIN LATERAL JSONB_ARRAY_ELEMENTS(COALESCE(rule_item -> 'whenAll', '[]'::jsonb)) AS condition_item
)
SELECT
    (SELECT COUNT(*) FROM typed_constraints tc LEFT JOIN immutable_attrs ia ON ia.attribute_code = tc.attribute_code WHERE ia.attribute_code IS NULL)
        AS stage4_typed_constraints_unknown_attribute_count,
    (SELECT COUNT(*) FROM immutable_attrs ia LEFT JOIN typed_constraints tc ON tc.attribute_code = ia.attribute_code WHERE tc.attribute_code IS NULL)
        AS stage4_typed_constraints_missing_from_immutable_count,
    (SELECT COUNT(*) FROM typed_constraints tc JOIN immutable_attrs ia ON ia.attribute_code = tc.attribute_code WHERE tc.value_type <> ia.value_type)
        AS stage4_typed_constraints_value_type_mismatch_count,
    (SELECT COUNT(*) FROM typed_constraints tc JOIN immutable_attrs ia ON ia.attribute_code = tc.attribute_code
      WHERE COALESCE(tc.expected_unit, '') <> COALESCE(ia.unit, ''))
        AS stage4_typed_constraints_unit_mismatch_count,
    (SELECT COUNT(*) FROM typed_constraints tc
      WHERE tc.min_value IS NOT NULL AND tc.max_value IS NOT NULL AND tc.min_value > tc.max_value)
        AS stage4_typed_constraints_invalid_range_count,
    (SELECT COUNT(*) FROM required_if_entries rie
      LEFT JOIN categories c ON c.code = rie.category_code
      WHERE rie.category_code IS NULL OR BTRIM(rie.category_code) = '' OR c.code IS NULL)
        AS stage4_typed_constraints_required_if_unknown_category_count,
    (SELECT COUNT(*) FROM required_if_entries rie
      LEFT JOIN immutable_attrs ia ON ia.attribute_code = LOWER(rie.condition_attribute_code)
      WHERE rie.condition_attribute_code IS NULL OR BTRIM(rie.condition_attribute_code) = '' OR ia.attribute_code IS NULL)
        AS stage4_typed_constraints_required_if_unknown_attribute_count;

-- 6) Stage 4 runtime execution observability presence.
SELECT
    CASE WHEN to_regclass('public.catalog_stage4_execution_metrics') IS NULL THEN 1 ELSE 0 END
        AS missing_stage4_execution_metrics_table_count;

-- 7) Stage 4 execution metrics by stream (last 24h window by metric_date).
SELECT
    stream,
    SUM(normalized_count) AS normalized_count,
    SUM(dropped_count) AS dropped_count,
    SUM(logical_dedup_count) AS logical_dedup_count,
    SUM(unknown_attribute_count) AS unknown_attribute_count
FROM catalog_stage4_execution_metrics
WHERE metric_date >= CURRENT_DATE - 1
GROUP BY stream
ORDER BY stream;

-- 8) Stage 4 ingest hard gate snapshot (last 24h).
SELECT
    COALESCE(SUM(normalized_count), 0) AS stage4_ingest_normalized_count_24h,
    COALESCE(SUM(dropped_count), 0) AS stage4_ingest_dropped_count_24h,
    COALESCE(SUM(logical_dedup_count), 0) AS stage4_ingest_logical_dedup_count_24h,
    COALESCE(SUM(unknown_attribute_count), 0) AS stage4_ingest_unknown_attribute_count_24h
FROM catalog_stage4_execution_metrics
WHERE metric_date >= CURRENT_DATE - 1
  AND stream IN ('OFFERS_INGEST', 'PRESET_EVENTS_INGEST');

-- 9) Stage 4 typed-violation reason codes (last 24h ingest window).
WITH recent_reasons AS (
    SELECT
        rc.reason_code
    FROM catalog_stage4_execution_metrics m
    CROSS JOIN LATERAL JSONB_ARRAY_ELEMENTS_TEXT(COALESCE(m.reason_codes, '[]'::jsonb)) AS rc(reason_code)
    WHERE m.metric_date >= CURRENT_DATE - 1
      AND m.stream IN ('OFFERS_INGEST', 'PRESET_EVENTS_INGEST')
)
SELECT
    COUNT(*) FILTER (WHERE reason_code LIKE 'OUT_OF_RANGE:%') AS out_of_range_count_24h,
    COUNT(*) FILTER (WHERE reason_code LIKE 'UNIT_MISMATCH:%') AS unit_mismatch_count_24h,
    COUNT(*) FILTER (WHERE reason_code LIKE 'PATTERN_MISMATCH:%') AS pattern_mismatch_count_24h
FROM recent_reasons;
