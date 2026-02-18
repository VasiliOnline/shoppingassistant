-- Публичные профили и агрегаты продавца
CREATE TABLE IF NOT EXISTS user_profiles (
    user_id BIGINT PRIMARY KEY REFERENCES auth_users(id) ON DELETE CASCADE,
    display_name TEXT NULL,
    avatar_url TEXT NULL,
    country_code VARCHAR(8) NULL,
    city TEXT NULL
);

CREATE TABLE IF NOT EXISTS seller_stats (
    user_id BIGINT PRIMARY KEY REFERENCES auth_users(id) ON DELETE CASCADE,
    rating_value DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    rating_count INT NOT NULL DEFAULT 0
);
