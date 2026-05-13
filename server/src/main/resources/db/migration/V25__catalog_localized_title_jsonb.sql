ALTER TABLE categories
    ADD COLUMN IF NOT EXISTS title_localized JSONB;

UPDATE categories
SET title_localized = jsonb_strip_nulls(
    jsonb_build_object(
        'ru', NULLIF(title_ru, ''),
        'en', NULLIF(title_en, '')
    )
)
WHERE title_localized IS NULL;

ALTER TABLE browse_nodes
    ADD COLUMN IF NOT EXISTS title_localized JSONB;

UPDATE browse_nodes
SET title_localized = jsonb_strip_nulls(
    jsonb_build_object(
        'ru', NULLIF(title_ru, ''),
        'en', NULLIF(title_en, '')
    )
)
WHERE title_localized IS NULL;

ALTER TABLE facet_definitions
    ADD COLUMN IF NOT EXISTS title_localized JSONB;

UPDATE facet_definitions
SET title_localized = jsonb_strip_nulls(
    jsonb_build_object(
        'ru', NULLIF(title_ru, ''),
        'en', NULLIF(title_en, '')
    )
)
WHERE title_localized IS NULL;

ALTER TABLE facet_presets
    ADD COLUMN IF NOT EXISTS title_localized JSONB;

UPDATE facet_presets
SET title_localized = jsonb_strip_nulls(
    jsonb_build_object(
        'ru', NULLIF(title_ru, ''),
        'en', NULLIF(title_en, '')
    )
)
WHERE title_localized IS NULL;

ALTER TABLE facet_collections
    ADD COLUMN IF NOT EXISTS title_localized JSONB;

UPDATE facet_collections
SET title_localized = jsonb_strip_nulls(
    jsonb_build_object(
        'ru', NULLIF(title_ru, ''),
        'en', NULLIF(title_en, '')
    )
)
WHERE title_localized IS NULL;
