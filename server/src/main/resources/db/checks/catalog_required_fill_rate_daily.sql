-- Daily fill-rate for required category attributes.
-- Output: category_code + attribute_code + fill_rate_pct.

WITH required_attributes AS (
    SELECT
        ca.category_code,
        ca.attribute_code
    FROM category_attributes ca
    WHERE ca.is_required = TRUE
),
recent_offers AS (
    SELECT
        o.id AS offer_id,
        p.category AS category_code,
        p.brand,
        p.model,
        p.title_norm,
        COALESCE(o.attributes, p.specs, '{}'::jsonb) AS attrs
    FROM offers o
    JOIN products p ON p.id = o.product_id
    WHERE TO_TIMESTAMP(o.updated_at / 1000.0) >= (NOW() - INTERVAL '1 day')
),
offer_required_rows AS (
    SELECT
        ro.offer_id,
        ro.category_code,
        ra.attribute_code,
        CASE
            WHEN ra.attribute_code = 'brand' THEN NULLIF(BTRIM(ro.brand), '') IS NOT NULL
            WHEN ra.attribute_code = 'model' THEN NULLIF(BTRIM(ro.model), '') IS NOT NULL
            WHEN ra.attribute_code = 'product_name' THEN NULLIF(BTRIM(ro.title_norm), '') IS NOT NULL
            ELSE (
                ro.attrs ? ra.attribute_code
                AND NULLIF(
                    BTRIM(
                        CASE
                            WHEN JSONB_TYPEOF(ro.attrs -> ra.attribute_code) = 'string'
                                THEN ro.attrs ->> ra.attribute_code
                            ELSE ro.attrs -> ra.attribute_code #>> '{}'
                        END
                    ),
                    ''
                ) IS NOT NULL
            )
        END AS is_filled
    FROM recent_offers ro
    JOIN required_attributes ra
        ON ra.category_code = ro.category_code
)
SELECT
    CURRENT_DATE AS report_date,
    category_code,
    attribute_code,
    COUNT(*) AS offer_count,
    SUM(CASE WHEN is_filled THEN 1 ELSE 0 END) AS filled_count,
    ROUND(
        100.0 * SUM(CASE WHEN is_filled THEN 1 ELSE 0 END)::numeric / NULLIF(COUNT(*), 0),
        2
    ) AS fill_rate_pct
FROM offer_required_rows
GROUP BY category_code, attribute_code
ORDER BY category_code, attribute_code;
