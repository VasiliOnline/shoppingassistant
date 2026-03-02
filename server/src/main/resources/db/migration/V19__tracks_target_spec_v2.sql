ALTER TABLE IF EXISTS tracks
    ADD COLUMN IF NOT EXISTS target_category_code VARCHAR(64);

ALTER TABLE IF EXISTS tracks
    ADD COLUMN IF NOT EXISTS target_attributes_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb;

UPDATE tracks
SET target_category_code = UPPER(
    NULLIF(
        TRIM(
            COALESCE(
                target_category_code,
                category_code,
                target #>> '{spec,categoryCode}',
                target ->> 'categoryCode'
            )
        ),
        ''
    )
)
WHERE
    target_category_code IS NULL
    OR target_category_code <> UPPER(
        NULLIF(
            TRIM(
                COALESCE(
                    target_category_code,
                    category_code,
                    target #>> '{spec,categoryCode}',
                    target ->> 'categoryCode'
                )
            ),
            ''
        )
    );

UPDATE tracks
SET target_attributes_jsonb = CASE
    WHEN jsonb_typeof(target #> '{spec,attributes}') = 'object' THEN target #> '{spec,attributes}'
    WHEN jsonb_typeof(target -> 'attributes') = 'object' THEN target -> 'attributes'
    WHEN jsonb_typeof(filters -> 'extra') = 'object' THEN filters -> 'extra'
    ELSE '{}'::jsonb
END
WHERE target_attributes_jsonb = '{}'::jsonb;

UPDATE tracks
SET category_code = target_category_code
WHERE category_code IS NULL AND target_category_code IS NOT NULL;

UPDATE tracks
SET target = jsonb_strip_nulls(
    target || jsonb_build_object(
        'spec', jsonb_build_object(
            'categoryCode', target_category_code,
            'attributes', target_attributes_jsonb,
            'matchKey', match_key
        ),
        'categoryCode', target_category_code,
        'attributes', target_attributes_jsonb,
        'matchKey', match_key
    )
)
WHERE target IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tracks_user_target_category_code
    ON tracks(user_id, target_category_code);

CREATE INDEX IF NOT EXISTS idx_tracks_target_attributes_gin
    ON tracks USING GIN (target_attributes_jsonb);
