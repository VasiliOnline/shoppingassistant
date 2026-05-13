ALTER TABLE categories
    ADD COLUMN IF NOT EXISTS title_ru VARCHAR(255) NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'categories'
          AND column_name = 'title'
    ) THEN
        EXECUTE $sql$
            UPDATE categories
            SET title_ru = COALESCE(NULLIF(BTRIM(title_ru), ''), NULLIF(BTRIM(title), ''))
        $sql$;

        EXECUTE 'ALTER TABLE categories DROP COLUMN title';
    END IF;
END $$;
