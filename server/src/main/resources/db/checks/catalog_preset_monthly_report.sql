-- Monthly report for preset performance and guardrails.
-- Window: previous full calendar month.
WITH params AS (
    SELECT
        DATE_TRUNC('month', CURRENT_DATE - INTERVAL '1 month')::date AS date_from,
        DATE_TRUNC('month', CURRENT_DATE)::date AS date_to
),
events AS (
    SELECT
        e.event_type,
        e.category_code,
        e.facet_preset_code,
        COALESCE(NULLIF(BTRIM(e.data_version), ''), 'unknown') AS data_version,
        CASE
            WHEN e.facet_preset_code ILIKE '%.DEFAULT' THEN 'DEFAULT'
            ELSE 'NON_DEFAULT'
        END AS preset_variant
    FROM catalog_preset_events e
    JOIN params p
        ON e.event_date >= p.date_from
       AND e.event_date < p.date_to
    WHERE e.facet_preset_code IS NOT NULL
),
preset_metrics AS (
    SELECT
        data_version,
        category_code,
        facet_preset_code,
        preset_variant,
        COUNT(*) FILTER (WHERE event_type = 'IMPRESSION') AS impressions,
        COUNT(*) FILTER (WHERE event_type = 'CLICK') AS clicks,
        COUNT(*) FILTER (WHERE event_type = 'CONVERSION') AS conversions
    FROM events
    GROUP BY data_version, category_code, facet_preset_code, preset_variant
),
variant_metrics AS (
    SELECT
        data_version,
        category_code,
        preset_variant,
        SUM(impressions) AS impressions,
        SUM(clicks) AS clicks,
        SUM(conversions) AS conversions
    FROM preset_metrics
    GROUP BY data_version, category_code, preset_variant
)
SELECT
    v.data_version,
    p.date_from,
    (p.date_to - INTERVAL '1 day')::date AS date_to,
    v.category_code,
    v.preset_variant,
    v.impressions,
    v.clicks,
    v.conversions,
    ROUND((v.clicks::numeric / NULLIF(v.impressions, 0)::numeric), 6) AS ctr,
    ROUND((v.conversions::numeric / NULLIF(v.impressions, 0)::numeric), 6) AS cvr
FROM variant_metrics v
CROSS JOIN params p
ORDER BY v.data_version, v.category_code, v.preset_variant;

-- Category-level default vs non-default uplift summary.
WITH params AS (
    SELECT
        DATE_TRUNC('month', CURRENT_DATE - INTERVAL '1 month')::date AS date_from,
        DATE_TRUNC('month', CURRENT_DATE)::date AS date_to
),
events AS (
    SELECT
        COALESCE(NULLIF(BTRIM(e.data_version), ''), 'unknown') AS data_version,
        e.category_code,
        CASE
            WHEN e.facet_preset_code ILIKE '%.DEFAULT' THEN 'DEFAULT'
            ELSE 'NON_DEFAULT'
        END AS preset_variant,
        e.event_type
    FROM catalog_preset_events e
    JOIN params p
        ON e.event_date >= p.date_from
       AND e.event_date < p.date_to
    WHERE e.facet_preset_code IS NOT NULL
),
variant_metrics AS (
    SELECT
        data_version,
        category_code,
        preset_variant,
        COUNT(*) FILTER (WHERE event_type = 'IMPRESSION') AS impressions,
        COUNT(*) FILTER (WHERE event_type = 'CLICK') AS clicks,
        COUNT(*) FILTER (WHERE event_type = 'CONVERSION') AS conversions
    FROM events
    GROUP BY data_version, category_code, preset_variant
),
pivoted AS (
    SELECT
        data_version,
        category_code,
        MAX(CASE WHEN preset_variant = 'DEFAULT' THEN impressions END) AS default_impressions,
        MAX(CASE WHEN preset_variant = 'DEFAULT' THEN clicks END) AS default_clicks,
        MAX(CASE WHEN preset_variant = 'DEFAULT' THEN conversions END) AS default_conversions,
        MAX(CASE WHEN preset_variant = 'NON_DEFAULT' THEN impressions END) AS non_default_impressions,
        MAX(CASE WHEN preset_variant = 'NON_DEFAULT' THEN clicks END) AS non_default_clicks,
        MAX(CASE WHEN preset_variant = 'NON_DEFAULT' THEN conversions END) AS non_default_conversions
    FROM variant_metrics
    GROUP BY data_version, category_code
)
SELECT
    data_version,
    category_code,
    default_impressions,
    non_default_impressions,
    ROUND((default_clicks::numeric / NULLIF(default_impressions, 0)::numeric), 6) AS default_ctr,
    ROUND((non_default_clicks::numeric / NULLIF(non_default_impressions, 0)::numeric), 6) AS non_default_ctr,
    ROUND((default_conversions::numeric / NULLIF(default_impressions, 0)::numeric), 6) AS default_cvr,
    ROUND((non_default_conversions::numeric / NULLIF(non_default_impressions, 0)::numeric), 6) AS non_default_cvr
FROM pivoted
ORDER BY data_version, category_code;

-- Guardrail: zero-results rate by category for the same window.
WITH params AS (
    SELECT
        DATE_TRUNC('month', CURRENT_DATE - INTERVAL '1 month')::date AS date_from,
        DATE_TRUNC('month', CURRENT_DATE)::date AS date_to
),
zero_guardrail AS (
    SELECT
        COALESCE(t.category_code, 'UNKNOWN') AS category_code,
        COUNT(*) AS snapshots_total,
        COUNT(*) FILTER (
            WHERE JSONB_ARRAY_LENGTH(COALESCE(s.items_json, '[]'::jsonb)) = 0
        ) AS zero_results_snapshots
    FROM track_top10_snapshots s
    JOIN tracks t
        ON t.id = s.track_id
    JOIN params p
        ON TO_TIMESTAMP(s.computed_at / 1000.0)::date >= p.date_from
       AND TO_TIMESTAMP(s.computed_at / 1000.0)::date < p.date_to
    GROUP BY COALESCE(t.category_code, 'UNKNOWN')
)
SELECT
    category_code,
    snapshots_total,
    zero_results_snapshots,
    ROUND((zero_results_snapshots::numeric / NULLIF(snapshots_total, 0)::numeric), 6) AS zero_results_rate,
    CASE
        WHEN (zero_results_snapshots::numeric / NULLIF(snapshots_total, 0)::numeric) <= 0.15 THEN 'PASS'
        ELSE 'FAIL'
    END AS guardrail_status
FROM zero_guardrail
ORDER BY category_code;
