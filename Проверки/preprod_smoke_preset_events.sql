-- Smoke seed for preset observability on local preprod DB.
-- Target DB: shoppingassistant_preprod_local

BEGIN;

-- 1) Minimal catalog context for events.
INSERT INTO categories (code, segment, title, parent_code, description, status)
VALUES ('FOOD.READY_MEALS', 'FOOD', 'Ready meals', NULL, 'Smoke seed category', 'ACTIVE')
ON CONFLICT (code) DO UPDATE
SET
    segment = EXCLUDED.segment,
    title = EXCLUDED.title,
    status = EXCLUDED.status;

INSERT INTO facet_presets (
    preset_code,
    category_code,
    title_ru,
    order_index,
    rules,
    notes,
    effective_from,
    effective_to
)
VALUES
    (
        'FP.FOOD.READY.DEFAULT',
        'FOOD.READY_MEALS',
        'Smoke default',
        10,
        '[]'::jsonb,
        'smoke-seed',
        '2026-01-01',
        NULL
    ),
    (
        'FP.FOOD.READY.PIZZA',
        'FOOD.READY_MEALS',
        'Smoke pizza',
        20,
        '[]'::jsonb,
        'smoke-seed',
        '2026-01-01',
        NULL
    )
ON CONFLICT (preset_code) DO UPDATE
SET
    category_code = EXCLUDED.category_code,
    title_ru = EXCLUDED.title_ru,
    notes = EXCLUDED.notes,
    effective_from = EXCLUDED.effective_from;

INSERT INTO facet_collections (
    collection_code,
    category_code,
    title_ru,
    browse_code,
    preset_code,
    order_index,
    tags,
    notes
)
VALUES (
    'B.FOOD.READY',
    'FOOD.READY_MEALS',
    'Smoke ready collection',
    'FOOD_READY',
    'FP.FOOD.READY.DEFAULT',
    10,
    '[]'::jsonb,
    'smoke-seed'
)
ON CONFLICT (collection_code) DO UPDATE
SET
    category_code = EXCLUDED.category_code,
    preset_code = EXCLUDED.preset_code,
    notes = EXCLUDED.notes;

-- 2) Reset previous smoke rows.
DELETE FROM catalog_preset_events
WHERE idempotency_key LIKE 'smoke|%';

DELETE FROM track_top10_snapshots
WHERE track_id BETWEEN 990001 AND 990010;

DELETE FROM tracks
WHERE id BETWEEN 990001 AND 990010;

-- 3) Monthly report payload (previous full month: January 2026).
WITH ts AS (
    SELECT
        (EXTRACT(EPOCH FROM TIMESTAMPTZ '2026-01-15 12:00:00+00') * 1000)::bigint AS jan_base_ms,
        (EXTRACT(EPOCH FROM TIMESTAMPTZ '2026-02-20 10:00:00+00') * 1000)::bigint AS today_base_ms
)
INSERT INTO catalog_preset_events (
    idempotency_key,
    event_type,
    query_session_id,
    category_code,
    facet_collection_code,
    facet_preset_code,
    offer_id,
    position,
    occurred_at,
    received_at,
    event_date,
    data_version,
    payload_json
)
SELECT
    v.idempotency_key,
    v.event_type,
    v.query_session_id,
    'FOOD.READY_MEALS',
    'B.FOOD.READY',
    v.facet_preset_code,
    v.offer_id,
    v.position,
    v.occurred_at,
    v.received_at,
    v.event_date,
    '2.2.5',
    '{}'::jsonb
FROM ts
CROSS JOIN LATERAL (
    VALUES
        -- January DEFAULT chain.
        ('smoke|jan|imp|default|offer-a|1', 'IMPRESSION', 'smoke-jan-default', 'FP.FOOD.READY.DEFAULT', 'offer-a', 1, ts.jan_base_ms + 1000, ts.jan_base_ms + 3000, DATE '2026-01-15'),
        ('smoke|jan|clk|default|offer-a|1', 'CLICK',      'smoke-jan-default', 'FP.FOOD.READY.DEFAULT', 'offer-a', 1, ts.jan_base_ms + 4000, ts.jan_base_ms + 5000, DATE '2026-01-15'),
        ('smoke|jan|cnv|default',          'CONVERSION',  'smoke-jan-default', 'FP.FOOD.READY.DEFAULT', NULL,      NULL, ts.jan_base_ms + 7000, ts.jan_base_ms + 8000, DATE '2026-01-15'),
        -- January NON-DEFAULT chain.
        ('smoke|jan|imp|pizza|offer-b|1',  'IMPRESSION',  'smoke-jan-pizza',   'FP.FOOD.READY.PIZZA',   'offer-b', 1, ts.jan_base_ms + 11000, ts.jan_base_ms + 12000, DATE '2026-01-15'),
        ('smoke|jan|imp|pizza|offer-c|2',  'IMPRESSION',  'smoke-jan-pizza',   'FP.FOOD.READY.PIZZA',   'offer-c', 2, ts.jan_base_ms + 13000, ts.jan_base_ms + 14000, DATE '2026-01-15'),
        ('smoke|jan|clk|pizza|offer-b|1',  'CLICK',       'smoke-jan-pizza',   'FP.FOOD.READY.PIZZA',   'offer-b', 1, ts.jan_base_ms + 15000, ts.jan_base_ms + 16000, DATE '2026-01-15'),
        ('smoke|jan|cnv|pizza',            'CONVERSION',  'smoke-jan-pizza',   'FP.FOOD.READY.PIZZA',   NULL,      NULL, ts.jan_base_ms + 17000, ts.jan_base_ms + 18000, DATE '2026-01-15'),
        -- Today chain (for 24h event counters).
        ('smoke|today|imp|default|offer-z|1', 'IMPRESSION', 'smoke-today-default', 'FP.FOOD.READY.DEFAULT', 'offer-z', 1, ts.today_base_ms + 1000, ts.today_base_ms + 2000, DATE '2026-02-20'),
        ('smoke|today|clk|default|offer-z|1', 'CLICK',      'smoke-today-default', 'FP.FOOD.READY.DEFAULT', 'offer-z', 1, ts.today_base_ms + 3000, ts.today_base_ms + 4000, DATE '2026-02-20'),
        ('smoke|today|cnv|default',           'CONVERSION',  'smoke-today-default', 'FP.FOOD.READY.DEFAULT', NULL,      NULL, ts.today_base_ms + 5000, ts.today_base_ms + 6000, DATE '2026-02-20')
) AS v(
    idempotency_key,
    event_type,
    query_session_id,
    facet_preset_code,
    offer_id,
    position,
    occurred_at,
    received_at,
    event_date
);

-- 4) Guardrail payload (10 snapshots, 1 zero-result => 10% PASS).
INSERT INTO tracks (id, category_code)
SELECT g, 'FOOD.READY_MEALS'
FROM generate_series(990001, 990010) AS g;

INSERT INTO track_top10_snapshots (
    track_id,
    computed_at,
    items_json,
    explanation_json,
    source_stamps_json,
    freshness_sec,
    locked_by,
    lock_until,
    last_attempt_at,
    last_success_at,
    fail_count,
    next_retry_at,
    last_error
)
SELECT
    g AS track_id,
    (EXTRACT(EPOCH FROM TIMESTAMPTZ '2026-01-20 10:00:00+00') * 1000)::bigint + ((g - 990001) * 1000) AS computed_at,
    CASE
        WHEN g = 990001 THEN '[]'::jsonb
        ELSE '[{"id":"offer-smoke"}]'::jsonb
    END AS items_json,
    '{"summary":"smoke"}'::jsonb AS explanation_json,
    '{}'::jsonb AS source_stamps_json,
    120 AS freshness_sec,
    NULL AS locked_by,
    NULL AS lock_until,
    (EXTRACT(EPOCH FROM TIMESTAMPTZ '2026-01-20 10:00:00+00') * 1000)::bigint + ((g - 990001) * 1000) AS last_attempt_at,
    (EXTRACT(EPOCH FROM TIMESTAMPTZ '2026-01-20 10:00:00+00') * 1000)::bigint + ((g - 990001) * 1000) AS last_success_at,
    0 AS fail_count,
    NULL AS next_retry_at,
    NULL AS last_error
FROM generate_series(990001, 990010) AS g;

COMMIT;
