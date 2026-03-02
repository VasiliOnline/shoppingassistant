-- Ensure Nearby geo queries can use GiST even when SQL uses COALESCE(location_geog, point(lat, lon)).
CREATE INDEX IF NOT EXISTS idx_offers_geo_coalesce_gix
    ON offers
    USING GIST (
        (
            COALESCE(
                location_geog,
                ST_SetSRID(ST_MakePoint(lon, lat), 4326)::geography
            )
        )
    )
    WHERE status = 'ACTIVE'
      AND (
        location_geog IS NOT NULL
        OR (lat IS NOT NULL AND lon IS NOT NULL)
      );
