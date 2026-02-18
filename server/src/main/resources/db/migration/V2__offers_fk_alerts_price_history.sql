-- Migrate user_id to BIGINT and add FKs to auth_users
ALTER TABLE offers
    ALTER COLUMN user_id TYPE BIGINT USING user_id::BIGINT,
    ADD CONSTRAINT fk_offers_user FOREIGN KEY (user_id) REFERENCES auth_users(id) ON DELETE RESTRICT;

ALTER TABLE user_preferences
    ALTER COLUMN user_id TYPE BIGINT USING user_id::BIGINT,
    ADD CONSTRAINT fk_user_prefs_user FOREIGN KEY (user_id) REFERENCES auth_users(id) ON DELETE CASCADE;

ALTER TABLE user_reviews
    ALTER COLUMN from_user_id TYPE BIGINT USING from_user_id::BIGINT,
    ALTER COLUMN to_user_id TYPE BIGINT USING to_user_id::BIGINT,
    ADD CONSTRAINT fk_reviews_from FOREIGN KEY (from_user_id) REFERENCES auth_users(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_reviews_to FOREIGN KEY (to_user_id) REFERENCES auth_users(id) ON DELETE CASCADE;

-- Alerts storage
CREATE TABLE IF NOT EXISTS alerts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    scope VARCHAR(16) NOT NULL, -- PRODUCT / OFFER
    product_id BIGINT NULL REFERENCES products(id) ON DELETE CASCADE,
    offer_id BIGINT NULL REFERENCES offers(id) ON DELETE CASCADE,
    alert_type VARCHAR(32) NOT NULL,
    threshold_value DOUBLE PRECISION NOT NULL,
    currency VARCHAR(8) NULL,
    delivery_channel VARCHAR(16) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_triggered_at BIGINT NULL,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)
);

-- Price history for analytics
CREATE TABLE IF NOT EXISTS offer_price_history (
    id BIGSERIAL PRIMARY KEY,
    offer_id BIGINT NOT NULL REFERENCES offers(id) ON DELETE CASCADE,
    price_minor BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    collected_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000),
    source VARCHAR(64) NULL
);

CREATE INDEX IF NOT EXISTS idx_offer_price_history_offer ON offer_price_history(offer_id);
