-- Geo/privacy columns for Nearby v2 (PostGIS)
CREATE EXTENSION IF NOT EXISTS postgis;

-- User geo snapshot (profile-level)
ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS lat DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS lon DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS geo_updated_at BIGINT,
    ADD COLUMN IF NOT EXISTS geo_source VARCHAR(16),
    ADD COLUMN IF NOT EXISTS geo_accuracy_m INT;

-- Offer geo snapshot (offer-level)
ALTER TABLE offers
    ADD COLUMN IF NOT EXISTS location_geog GEOGRAPHY(Point, 4326),
    ADD COLUMN IF NOT EXISTS location_updated_at BIGINT,
    ADD COLUMN IF NOT EXISTS location_privacy VARCHAR(16) NOT NULL DEFAULT 'COARSE';

-- Backfill geography + timestamps for existing rows
UPDATE offers
SET location_geog = ST_SetSRID(ST_MakePoint(lon, lat), 4326)::geography
WHERE location_geog IS NULL AND lat IS NOT NULL AND lon IS NOT NULL;

UPDATE offers
SET location_updated_at = updated_at
WHERE location_updated_at IS NULL AND updated_at IS NOT NULL;

-- PostGIS index for radius/distance filters
CREATE INDEX IF NOT EXISTS idx_offers_location_geog_gix
    ON offers USING GIST (location_geog)
    WHERE status = 'ACTIVE';
