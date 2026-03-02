-- Catalog model closed-loop backlog from production data.
-- Builds action queues for:
-- 1) zero-results,
-- 2) unknown attribute keys in live offers,
-- 3) normalization conflicts for dictionary-backed attributes.

-- 1) Zero-results backlog (from track top10 snapshots).
WITH zero_results AS (
    SELECT
        t.category_code,
        s.track_id,
        s.computed_at,
        s.last_error,
        s.fail_count,
        s.explanation_json ->> 'summary' AS summary
    FROM track_top10_snapshots s
    JOIN tracks t ON t.id = s.track_id
    WHERE JSONB_ARRAY_LENGTH(COALESCE(s.items_json, '[]'::jsonb)) = 0
)
SELECT
    'ZERO_RESULTS' AS issue_type,
    COALESCE(category_code, 'UNKNOWN') AS category_code,
    COALESCE(summary, last_error, 'no-items') AS issue_key,
    COUNT(*) AS issue_count,
    MIN(TO_TIMESTAMP(computed_at / 1000.0)) AS first_seen_at,
    MAX(TO_TIMESTAMP(computed_at / 1000.0)) AS last_seen_at,
    (CURRENT_DATE + INTERVAL '3 day')::date AS sla_due_date
FROM zero_results
GROUP BY COALESCE(category_code, 'UNKNOWN'), COALESCE(summary, last_error, 'no-items')
ORDER BY issue_count DESC, last_seen_at DESC;

-- 2) Unknown attribute keys in offers relative to category profile.
WITH offer_attrs AS (
    SELECT
        p.category AS category_code,
        attr.key AS attribute_code
    FROM offers o
    JOIN products p ON p.id = o.product_id
    CROSS JOIN LATERAL JSONB_EACH(COALESCE(o.attributes, '{}'::jsonb)) AS attr(key, value)
),
known_category_attrs AS (
    SELECT
        category_code,
        attribute_code
    FROM category_attributes
)
SELECT
    'UNKNOWN_ATTRIBUTE' AS issue_type,
    oa.category_code,
    oa.attribute_code AS issue_key,
    COUNT(*) AS issue_count,
    (CURRENT_DATE + INTERVAL '7 day')::date AS sla_due_date
FROM offer_attrs oa
LEFT JOIN known_category_attrs kca
    ON kca.category_code = oa.category_code
   AND kca.attribute_code = oa.attribute_code
WHERE kca.attribute_code IS NULL
GROUP BY oa.category_code, oa.attribute_code
ORDER BY issue_count DESC, oa.category_code, oa.attribute_code;

-- 3) Normalization conflicts for dictionary-backed attributes.
WITH offer_attrs AS (
    SELECT
        p.category AS category_code,
        attr.key AS attribute_code,
        LOWER(BTRIM(
            CASE
                WHEN JSONB_TYPEOF(attr.value) = 'string' THEN attr.value #>> '{}'
                WHEN JSONB_TYPEOF(attr.value) IN ('number', 'boolean') THEN attr.value::text
                ELSE ''
            END
        )) AS raw_value
    FROM offers o
    JOIN products p ON p.id = o.product_id
    CROSS JOIN LATERAL JSONB_EACH(COALESCE(o.attributes, '{}'::jsonb)) AS attr(key, value)
    WHERE JSONB_TYPEOF(attr.value) IN ('string', 'number', 'boolean')
      AND BTRIM(
          CASE
              WHEN JSONB_TYPEOF(attr.value) = 'string' THEN attr.value #>> '{}'
              ELSE attr.value::text
          END
      ) <> ''
),
dict_tokens AS (
    SELECT
        LOWER(attribute_code) AS attribute_code,
        LOWER(canonical_code) AS token
    FROM attribute_value_dict
    UNION ALL
    SELECT
        LOWER(attribute_code) AS attribute_code,
        LOWER(canonical_value) AS token
    FROM attribute_value_dict
    UNION ALL
    SELECT
        LOWER(d.attribute_code) AS attribute_code,
        LOWER(elem.value) AS token
    FROM attribute_value_dict d
    CROSS JOIN LATERAL JSONB_ARRAY_ELEMENTS_TEXT(COALESCE(d.synonyms, '[]'::jsonb)) AS elem(value)
),
dict_attrs AS (
    SELECT DISTINCT LOWER(attribute_code) AS attribute_code
    FROM attribute_value_dict
)
SELECT
    'NORMALIZATION_CONFLICT' AS issue_type,
    oa.category_code,
    oa.attribute_code || ':' || oa.raw_value AS issue_key,
    COUNT(*) AS issue_count,
    (CURRENT_DATE + INTERVAL '7 day')::date AS sla_due_date
FROM offer_attrs oa
JOIN dict_attrs da
    ON da.attribute_code = LOWER(oa.attribute_code)
LEFT JOIN dict_tokens dt
    ON dt.attribute_code = LOWER(oa.attribute_code)
   AND dt.token = oa.raw_value
WHERE dt.token IS NULL
GROUP BY oa.category_code, oa.attribute_code, oa.raw_value
ORDER BY issue_count DESC, oa.category_code, oa.attribute_code, oa.raw_value;

-- 4) Stage 4 closed-set unknown values in live offers.
WITH offer_attrs AS (
    SELECT
        p.category AS category_code,
        LOWER(BTRIM(attr.key)) AS attribute_code,
        LOWER(BTRIM(
            CASE
                WHEN JSONB_TYPEOF(attr.value) = 'string' THEN attr.value #>> '{}'
                WHEN JSONB_TYPEOF(attr.value) IN ('number', 'boolean') THEN attr.value::text
                ELSE ''
            END
        )) AS raw_value
    FROM offers o
    JOIN products p ON p.id = o.product_id
    CROSS JOIN LATERAL JSONB_EACH(COALESCE(o.attributes, '{}'::jsonb)) AS attr(key, value)
    WHERE JSONB_TYPEOF(attr.value) IN ('string', 'number', 'boolean')
      AND BTRIM(
          CASE
              WHEN JSONB_TYPEOF(attr.value) = 'string' THEN attr.value #>> '{}'
              ELSE attr.value::text
          END
      ) <> ''
),
stage4_closed_set_attrs AS (
    SELECT LOWER(attribute_code) AS attribute_code
    FROM catalog_stage4_normalization_rules
    WHERE dictionary_backed = TRUE
      AND accepts_free_text = FALSE
),
dict_tokens AS (
    SELECT LOWER(attribute_code) AS attribute_code, LOWER(canonical_code) AS token
    FROM attribute_value_dict
    UNION ALL
    SELECT LOWER(attribute_code) AS attribute_code, LOWER(canonical_value) AS token
    FROM attribute_value_dict
    UNION ALL
    SELECT LOWER(d.attribute_code) AS attribute_code, LOWER(elem.value) AS token
    FROM attribute_value_dict d
    CROSS JOIN LATERAL JSONB_ARRAY_ELEMENTS_TEXT(COALESCE(d.synonyms, '[]'::jsonb)) AS elem(value)
)
SELECT
    'STAGE4_UNKNOWN_CLOSED_SET_VALUE' AS issue_type,
    oa.category_code,
    oa.attribute_code || ':' || oa.raw_value AS issue_key,
    COUNT(*) AS issue_count,
    (CURRENT_DATE + INTERVAL '3 day')::date AS sla_due_date
FROM offer_attrs oa
JOIN stage4_closed_set_attrs s4
    ON s4.attribute_code = oa.attribute_code
LEFT JOIN dict_tokens dt
    ON dt.attribute_code = oa.attribute_code
   AND dt.token = oa.raw_value
WHERE dt.token IS NULL
GROUP BY oa.category_code, oa.attribute_code, oa.raw_value
ORDER BY issue_count DESC, oa.category_code, oa.attribute_code, oa.raw_value;

-- 5) Stage 4 incompatible values by immutable value_type.
WITH offer_attrs AS (
    SELECT
        p.category AS category_code,
        LOWER(BTRIM(attr.key)) AS attribute_code,
        BTRIM(
            CASE
                WHEN JSONB_TYPEOF(attr.value) = 'string' THEN attr.value #>> '{}'
                WHEN JSONB_TYPEOF(attr.value) IN ('number', 'boolean') THEN attr.value::text
                ELSE ''
            END
        ) AS raw_value
    FROM offers o
    JOIN products p ON p.id = o.product_id
    CROSS JOIN LATERAL JSONB_EACH(COALESCE(o.attributes, '{}'::jsonb)) AS attr(key, value)
    WHERE JSONB_TYPEOF(attr.value) IN ('string', 'number', 'boolean')
      AND BTRIM(
          CASE
              WHEN JSONB_TYPEOF(attr.value) = 'string' THEN attr.value #>> '{}'
              ELSE attr.value::text
          END
      ) <> ''
),
stage4_types AS (
    SELECT
        LOWER(attribute_code) AS attribute_code,
        value_type
    FROM catalog_stage4_immutable_attributes
),
typed_offer_attrs AS (
    SELECT
        oa.category_code,
        oa.attribute_code,
        oa.raw_value,
        st.value_type
    FROM offer_attrs oa
    JOIN stage4_types st
        ON st.attribute_code = oa.attribute_code
)
SELECT
    'STAGE4_INCOMPATIBLE_VALUE' AS issue_type,
    toa.category_code,
    toa.attribute_code || ':' || toa.raw_value AS issue_key,
    COUNT(*) AS issue_count,
    (CURRENT_DATE + INTERVAL '3 day')::date AS sla_due_date
FROM typed_offer_attrs toa
WHERE (toa.value_type = 'NUMBER' AND BTRIM(REGEXP_REPLACE(toa.raw_value, '\s+', '', 'g')) !~ '^-?[0-9]+([.,][0-9]+)?$')
   OR (toa.value_type = 'BOOLEAN' AND LOWER(BTRIM(toa.raw_value)) NOT IN (
        'true', 'false', '1', '0', 'yes', 'no', 'y', 'n', 'да', 'нет', 'истина', 'ложь'
   ))
GROUP BY toa.category_code, toa.attribute_code, toa.raw_value
ORDER BY issue_count DESC, toa.category_code, toa.attribute_code, toa.raw_value;

-- 6) Stage 4 typed constraints: OUT_OF_RANGE.
WITH offer_attrs AS (
    SELECT
        p.category AS category_code,
        LOWER(BTRIM(attr.key)) AS attribute_code,
        BTRIM(
            CASE
                WHEN JSONB_TYPEOF(attr.value) = 'string' THEN attr.value #>> '{}'
                WHEN JSONB_TYPEOF(attr.value) IN ('number', 'boolean') THEN attr.value::text
                ELSE ''
            END
        ) AS raw_value_text,
        CASE
            WHEN JSONB_TYPEOF(attr.value) = 'number' THEN (attr.value #>> '{}')::double precision
            WHEN JSONB_TYPEOF(attr.value) = 'string'
                 AND BTRIM(REPLACE(attr.value #>> '{}', ',', '.')) ~ '^-?[0-9]+(?:\.[0-9]+)?$'
                THEN (BTRIM(REPLACE(attr.value #>> '{}', ',', '.')))::double precision
            ELSE NULL
        END AS numeric_value
    FROM offers o
    JOIN products p ON p.id = o.product_id
    CROSS JOIN LATERAL JSONB_EACH(COALESCE(o.attributes, '{}'::jsonb)) AS attr(key, value)
),
typed_constraints AS (
    SELECT
        LOWER(attribute_code) AS attribute_code,
        min_value,
        max_value
    FROM catalog_stage4_typed_constraints
    WHERE min_value IS NOT NULL OR max_value IS NOT NULL
)
SELECT
    'OUT_OF_RANGE' AS issue_type,
    oa.category_code,
    oa.attribute_code || ':' || oa.raw_value_text AS issue_key,
    COUNT(*) AS issue_count,
    (CURRENT_DATE + INTERVAL '3 day')::date AS sla_due_date
FROM offer_attrs oa
JOIN typed_constraints tc
    ON tc.attribute_code = oa.attribute_code
WHERE oa.numeric_value IS NOT NULL
  AND (
    (tc.min_value IS NOT NULL AND oa.numeric_value < tc.min_value) OR
    (tc.max_value IS NOT NULL AND oa.numeric_value > tc.max_value)
  )
GROUP BY oa.category_code, oa.attribute_code, oa.raw_value_text
ORDER BY issue_count DESC, oa.category_code, oa.attribute_code, oa.raw_value_text;

-- 7) Stage 4 typed constraints: UNIT_MISMATCH.
WITH offer_attrs AS (
    SELECT
        p.category AS category_code,
        LOWER(BTRIM(attr.key)) AS attribute_code,
        BTRIM(
            CASE
                WHEN JSONB_TYPEOF(attr.value) = 'string' THEN attr.value #>> '{}'
                WHEN JSONB_TYPEOF(attr.value) IN ('number', 'boolean') THEN attr.value::text
                ELSE ''
            END
        ) AS raw_value_text
    FROM offers o
    JOIN products p ON p.id = o.product_id
    CROSS JOIN LATERAL JSONB_EACH(COALESCE(o.attributes, '{}'::jsonb)) AS attr(key, value)
    WHERE JSONB_TYPEOF(attr.value) IN ('string', 'number', 'boolean')
),
typed_constraints AS (
    SELECT
        LOWER(attribute_code) AS attribute_code,
        LOWER(BTRIM(expected_unit)) AS expected_unit
    FROM catalog_stage4_typed_constraints
    WHERE expected_unit IS NOT NULL
      AND BTRIM(expected_unit) <> ''
),
typed_with_units AS (
    SELECT
        oa.category_code,
        oa.attribute_code,
        oa.raw_value_text,
        tc.expected_unit,
        LOWER(
            BTRIM(
                REGEXP_REPLACE(
                    oa.raw_value_text,
                    '^\s*-?[0-9]+(?:[.,][0-9]+)?\s*',
                    ''
                )
            )
        ) AS raw_unit_token
    FROM offer_attrs oa
    JOIN typed_constraints tc
        ON tc.attribute_code = oa.attribute_code
)
SELECT
    'UNIT_MISMATCH' AS issue_type,
    twu.category_code,
    twu.attribute_code || ':' || twu.raw_value_text AS issue_key,
    COUNT(*) AS issue_count,
    (CURRENT_DATE + INTERVAL '3 day')::date AS sla_due_date
FROM typed_with_units twu
WHERE twu.raw_unit_token <> ''
  AND twu.raw_unit_token <> twu.expected_unit
GROUP BY twu.category_code, twu.attribute_code, twu.raw_value_text
ORDER BY issue_count DESC, twu.category_code, twu.attribute_code, twu.raw_value_text;

-- 8) Stage 4 typed constraints: PATTERN_MISMATCH.
WITH offer_attrs AS (
    SELECT
        p.category AS category_code,
        LOWER(BTRIM(attr.key)) AS attribute_code,
        BTRIM(
            CASE
                WHEN JSONB_TYPEOF(attr.value) = 'string' THEN attr.value #>> '{}'
                WHEN JSONB_TYPEOF(attr.value) IN ('number', 'boolean') THEN attr.value::text
                ELSE ''
            END
        ) AS raw_value_text
    FROM offers o
    JOIN products p ON p.id = o.product_id
    CROSS JOIN LATERAL JSONB_EACH(COALESCE(o.attributes, '{}'::jsonb)) AS attr(key, value)
    WHERE JSONB_TYPEOF(attr.value) IN ('string', 'number', 'boolean')
),
typed_constraints AS (
    SELECT
        LOWER(attribute_code) AS attribute_code,
        regex_pattern
    FROM catalog_stage4_typed_constraints
    WHERE regex_pattern IS NOT NULL
      AND BTRIM(regex_pattern) <> ''
)
SELECT
    'PATTERN_MISMATCH' AS issue_type,
    oa.category_code,
    oa.attribute_code || ':' || oa.raw_value_text AS issue_key,
    COUNT(*) AS issue_count,
    (CURRENT_DATE + INTERVAL '3 day')::date AS sla_due_date
FROM offer_attrs oa
JOIN typed_constraints tc
    ON tc.attribute_code = oa.attribute_code
WHERE oa.raw_value_text <> ''
  AND oa.raw_value_text !~ tc.regex_pattern
GROUP BY oa.category_code, oa.attribute_code, oa.raw_value_text
ORDER BY issue_count DESC, oa.category_code, oa.attribute_code, oa.raw_value_text;
