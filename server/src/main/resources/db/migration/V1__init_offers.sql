-- Base schema for auth/products/offers/users preferences (jsonb-friendly)
CREATE TABLE IF NOT EXISTS auth_users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NULL,
    phone VARCHAR(64) NULL,
    pending_phone VARCHAR(64) NULL,
    pending_phone_requested_at BIGINT NULL,
    phone_verified_at BIGINT NULL,
    avatar_url VARCHAR(512) NULL,
    city VARCHAR(255) NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified_at BIGINT NULL,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000),
    photo_urls JSONB NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deletion_requested_at BIGINT NULL,
    deletion_after BIGINT NULL,
    deletion_restore_token VARCHAR(255) NULL
);

CREATE TABLE IF NOT EXISTS auth_audit (
    id BIGSERIAL PRIMARY KEY,
    event VARCHAR(64) NOT NULL,
    user_id BIGINT NULL,
    email VARCHAR(255) NULL,
    ip VARCHAR(64) NULL,
    user_agent VARCHAR(512) NULL,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)
);

CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    brand TEXT NULL,
    model TEXT NULL,
    title_norm TEXT NOT NULL,
    image_urls JSONB NULL,
    specs JSONB NULL,
    description TEXT NULL,
    gtin TEXT NULL,
    mpn TEXT NULL,
    sku TEXT NULL,
    updated_at BIGINT NULL
);

CREATE INDEX IF NOT EXISTS idx_products_brand_model ON products(brand, model);
CREATE INDEX IF NOT EXISTS idx_products_title_norm ON products USING GIN (to_tsvector('simple', title_norm));
CREATE INDEX IF NOT EXISTS idx_products_specs_gin ON products USING GIN (specs);

CREATE TABLE IF NOT EXISTS product_i18n (
    product_id BIGINT REFERENCES products(id) ON DELETE CASCADE,
    lang VARCHAR(8) NOT NULL,
    title TEXT NOT NULL,
    description TEXT NULL,
    PRIMARY KEY (product_id, lang)
);

CREATE TABLE IF NOT EXISTS offers (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL,
    price_cents BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    attributes JSONB NULL,
    description TEXT NULL,
    image_urls JSONB NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    updated_at BIGINT NULL
);

CREATE INDEX IF NOT EXISTS idx_offers_product ON offers(product_id);
CREATE INDEX IF NOT EXISTS idx_offers_user ON offers(user_id);
CREATE INDEX IF NOT EXISTS idx_offers_price ON offers(price_cents);
CREATE INDEX IF NOT EXISTS idx_offers_attrs_gin ON offers USING GIN (attributes);

CREATE TABLE IF NOT EXISTS user_preferences (
    user_id TEXT PRIMARY KEY,
    badges JSONB NULL,
    shipping_countries JSONB NULL,
    rating_value DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    rating_count INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS user_reviews (
    id BIGSERIAL PRIMARY KEY,
    from_user_id TEXT NOT NULL,
    to_user_id TEXT NOT NULL,
    score INT NOT NULL,
    text TEXT NULL,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)
);

CREATE INDEX IF NOT EXISTS idx_user_reviews_to ON user_reviews(to_user_id);

CREATE TABLE IF NOT EXISTS tracks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    type VARCHAR(16) NOT NULL,
    match_key VARCHAR(256) NULL,
    category_code VARCHAR(64) NULL,
    target_category_code VARCHAR(64) NULL,
    target_attributes_jsonb JSONB NULL,
    target JSONB NOT NULL,
    filters JSONB NOT NULL,
    title VARCHAR(255) NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    dedup_key VARCHAR(512) NOT NULL,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000),
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000),
    last_checked_at BIGINT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tracks_user_dedup_key
    ON tracks(user_id, dedup_key);
CREATE INDEX IF NOT EXISTS idx_tracks_user_updated_at
    ON tracks(user_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_tracks_user_state
    ON tracks(user_id, state);
CREATE INDEX IF NOT EXISTS idx_tracks_user_match_key
    ON tracks(user_id, match_key);
CREATE INDEX IF NOT EXISTS idx_tracks_user_category_code
    ON tracks(user_id, category_code);
CREATE INDEX IF NOT EXISTS idx_tracks_user_target_category_code
    ON tracks(user_id, target_category_code);

CREATE TABLE IF NOT EXISTS track_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    track_id BIGINT NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
    type VARCHAR(32) NOT NULL,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000),
    title VARCHAR(255) NOT NULL,
    subtitle VARCHAR(512) NULL,
    dedup_key VARCHAR(512) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_track_events_track_dedup_key
    ON track_events(track_id, dedup_key);
CREATE INDEX IF NOT EXISTS idx_track_events_user_created_at
    ON track_events(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_track_events_track_created_at
    ON track_events(track_id, created_at);
CREATE INDEX IF NOT EXISTS idx_track_events_user_is_read
    ON track_events(user_id, is_read);
