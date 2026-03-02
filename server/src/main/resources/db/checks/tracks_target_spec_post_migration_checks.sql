-- Post-migration guards for tracks target spec v2.
-- 1) Empty target category code.
SELECT
    COUNT(*) AS empty_target_category_code
FROM tracks
WHERE COALESCE(NULLIF(TRIM(target_category_code), ''), NULLIF(TRIM(target #>> '{spec,categoryCode}'), '')) IS NULL;

-- Sample rows with empty target category code.
SELECT
    id,
    user_id,
    type,
    target_category_code,
    target #>> '{spec,categoryCode}' AS spec_category_code
FROM tracks
WHERE COALESCE(NULLIF(TRIM(target_category_code), ''), NULLIF(TRIM(target #>> '{spec,categoryCode}'), '')) IS NULL
ORDER BY id DESC
LIMIT 100;

-- 2) Target attributes quality and parity with target.spec.attributes.
SELECT
    COUNT(*) FILTER (WHERE jsonb_typeof(target_attributes_jsonb) <> 'object') AS non_object_target_attributes,
    COUNT(*) FILTER (WHERE jsonb_object_length(target_attributes_jsonb) > 64) AS too_many_target_attributes,
    COUNT(*) FILTER (
        WHERE jsonb_typeof(target #> '{spec,attributes}') = 'object'
          AND target_attributes_jsonb <> (target #> '{spec,attributes}')
    ) AS spec_column_mismatch
FROM tracks;

-- Sample mismatch rows between target_attributes_jsonb and target.spec.attributes.
SELECT
    id,
    user_id,
    target_attributes_jsonb,
    target #> '{spec,attributes}' AS spec_attributes
FROM tracks
WHERE jsonb_typeof(target #> '{spec,attributes}') = 'object'
  AND target_attributes_jsonb <> (target #> '{spec,attributes}')
ORDER BY id DESC
LIMIT 100;

