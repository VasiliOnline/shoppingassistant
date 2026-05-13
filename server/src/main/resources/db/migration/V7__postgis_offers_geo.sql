-- Enable PostGIS for geo radius/distance queries
CREATE EXTENSION IF NOT EXISTS postgis;

-- Geo + Nearby support columns
ALTER TABLE offers
    ADD COLUMN IF NOT EXISTS condition VARCHAR(16),
    ADD COLUMN IF NOT EXISTS delivery_channel VARCHAR(16),
    ADD COLUMN IF NOT EXISTS lat DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS lon DOUBLE PRECISION;

-- Indices for Nearby filters
CREATE INDEX IF NOT EXISTS idx_offers_condition ON offers(condition);
CREATE INDEX IF NOT EXISTS idx_offers_delivery_channel ON offers(delivery_channel);
CREATE INDEX IF NOT EXISTS idx_offers_lat_lon ON offers(lat, lon);

-- PostGIS index for ST_DWithin / ST_Distance
CREATE INDEX IF NOT EXISTS idx_offers_geo_gist
    ON offers USING GIST (((ST_SetSRID(ST_MakePoint(lon, lat), 4326))::geography))
    WHERE lat IS NOT NULL AND lon IS NOT NULL;
