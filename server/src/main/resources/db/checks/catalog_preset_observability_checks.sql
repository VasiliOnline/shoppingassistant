-- Production checks for preset observability data quality.
-- Focus: completeness, ingestion lag, referential consistency, dedup, and sequence sanity.

-- 1) Recent event volume by type (last 24h).
SELECT
    event_type,
    COUNT(*) AS event_count_24h
FROM catalog_preset_events
WHERE occurred_at >= (EXTRACT(EPOCH FROM NOW()) * 1000 - 24 * 60 * 60 * 1000)
GROUP BY event_type
ORDER BY event_type;

-- 2) Required fields validation.
SELECT
    COUNT(*) AS invalid_required_fields_count
FROM catalog_preset_events
WHERE BTRIM(idempotency_key) = ''
   OR BTRIM(query_session_id) = ''
   OR BTRIM(category_code) = ''
   OR BTRIM(COALESCE(facet_preset_code, '')) = ''
   OR occurred_at <= 0
   OR event_date IS NULL
   OR (position IS NOT NULL AND position <= 0);

-- 3) Event type validation.
SELECT
    COUNT(*) AS invalid_event_type_count
FROM catalog_preset_events
WHERE event_type NOT IN ('IMPRESSION', 'CLICK', 'CONVERSION');

-- 4) Ingestion lag > 6h.
SELECT
    COUNT(*) AS delayed_ingest_over_6h_count
FROM catalog_preset_events
WHERE received_at - occurred_at > 6 * 60 * 60 * 1000;

-- 5) Referential consistency checks.
SELECT
    COUNT(*) AS unknown_category_code_count
FROM catalog_preset_events e
LEFT JOIN categories c
    ON c.code = e.category_code
WHERE c.code IS NULL;

SELECT
    COUNT(*) AS unknown_facet_preset_code_count
FROM catalog_preset_events e
LEFT JOIN facet_presets p
    ON p.preset_code = e.facet_preset_code
WHERE e.facet_preset_code IS NOT NULL
  AND p.preset_code IS NULL;

SELECT
    COUNT(*) AS unknown_facet_collection_code_count
FROM catalog_preset_events e
LEFT JOIN facet_collections c
    ON c.collection_code = e.facet_collection_code
WHERE e.facet_collection_code IS NOT NULL
  AND c.collection_code IS NULL;

-- 6) Dedup sanity (must be zero because of unique index/event-day constraint).
WITH duplicate_keys AS (
    SELECT
        event_date,
        idempotency_key,
        COUNT(*) AS key_count
    FROM catalog_preset_events
    GROUP BY event_date, idempotency_key
    HAVING COUNT(*) > 1
)
SELECT
    COUNT(*) AS duplicate_idempotency_count
FROM duplicate_keys;

-- 7) Sequence sanity: click without impression in same session/preset/offer.
WITH clicks AS (
    SELECT
        query_session_id,
        facet_preset_code,
        offer_id
    FROM catalog_preset_events
    WHERE event_type = 'CLICK'
),
impressions AS (
    SELECT
        query_session_id,
        facet_preset_code,
        offer_id
    FROM catalog_preset_events
    WHERE event_type = 'IMPRESSION'
)
SELECT
    COUNT(*) AS click_without_impression_count
FROM clicks c
LEFT JOIN impressions i
    ON i.query_session_id = c.query_session_id
   AND i.facet_preset_code = c.facet_preset_code
   AND COALESCE(i.offer_id, '') = COALESCE(c.offer_id, '')
WHERE i.query_session_id IS NULL;

-- 8) Sequence sanity: conversion without click in same session/preset.
WITH conversions AS (
    SELECT
        query_session_id,
        facet_preset_code
    FROM catalog_preset_events
    WHERE event_type = 'CONVERSION'
),
clicks AS (
    SELECT
        query_session_id,
        facet_preset_code
    FROM catalog_preset_events
    WHERE event_type = 'CLICK'
)
SELECT
    COUNT(*) AS conversion_without_click_count
FROM conversions c
LEFT JOIN clicks cl
    ON cl.query_session_id = c.query_session_id
   AND cl.facet_preset_code = c.facet_preset_code
WHERE cl.query_session_id IS NULL;
