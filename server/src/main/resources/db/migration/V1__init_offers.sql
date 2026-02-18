-- Base schema for products/offers/users preferences (jsonb-friendly)
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
