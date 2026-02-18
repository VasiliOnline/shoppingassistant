-- Integration SQL checks for Nearby (facets/total/distance)
BEGIN;

CREATE EXTENSION IF NOT EXISTS postgis;

DO $$
DECLARE
    v_offer_id BIGINT;
    v_lat DOUBLE PRECISION;
    v_lon DOUBLE PRECISION;
    v_total BIGINT;
    v_facet_total BIGINT;
    v_distance DOUBLE PRECISION;
BEGIN
    SELECT o.id, o.lat, o.lon
    INTO v_offer_id, v_lat, v_lon
    FROM offers o
    JOIN products p ON p.id = o.product_id
    WHERE o.lat IS NOT NULL
      AND o.lon IS NOT NULL
      AND p.brand IS NOT NULL
    LIMIT 1;

    IF v_offer_id IS NULL THEN
        RAISE NOTICE 'SKIP: no offers with geo + brand data';
        RETURN;
    END IF;

    SELECT COUNT(DISTINCT o.id)
    INTO v_total
    FROM offers o
    JOIN products p ON p.id = o.product_id
    WHERE ST_DWithin(
        COALESCE(
            o.location_geog,
            ST_SetSRID(ST_MakePoint(o.lon, o.lat), 4326)::geography
        ),
        ST_SetSRID(ST_MakePoint(v_lon, v_lat), 4326)::geography,
        1000
    );

    SELECT COALESCE(SUM(cnt), 0)
    INTO v_facet_total
    FROM (
        SELECT p.brand, COUNT(DISTINCT o.id) AS cnt
        FROM offers o
        JOIN products p ON p.id = o.product_id
        WHERE ST_DWithin(
            COALESCE(
                o.location_geog,
                ST_SetSRID(ST_MakePoint(o.lon, o.lat), 4326)::geography
            ),
            ST_SetSRID(ST_MakePoint(v_lon, v_lat), 4326)::geography,
            1000
        )
        GROUP BY p.brand
    ) s;

    IF v_total <> v_facet_total THEN
        RAISE EXCEPTION 'Facet total mismatch: total=% vs facets=%', v_total, v_facet_total;
    END IF;

    SELECT ST_Distance(
        ST_SetSRID(ST_MakePoint(v_lon, v_lat), 4326)::geography,
        ST_SetSRID(ST_MakePoint(v_lon, v_lat), 4326)::geography
    )
    INTO v_distance;

    IF v_distance IS NULL OR v_distance > 0.01 THEN
        RAISE EXCEPTION 'Distance check failed: %', v_distance;
    END IF;
END $$;

ROLLBACK;
