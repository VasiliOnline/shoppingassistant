-- Cleanup for smoke seed created by preprod_smoke_preset_events.sql
-- Intended for isolated local preprod DB (shoppingassistant_preprod_local).

BEGIN;

-- 1) Remove observability smoke events.
DELETE FROM catalog_preset_events
WHERE idempotency_key LIKE 'smoke|%';

-- 2) Remove guardrail smoke snapshots/tracks.
DELETE FROM track_top10_snapshots
WHERE track_id BETWEEN 990001 AND 990010;

DELETE FROM tracks
WHERE id BETWEEN 990001 AND 990010;

-- 3) Remove smoke facet context only if still marked as smoke.
DELETE FROM facet_collections
WHERE collection_code = 'B.FOOD.READY'
  AND notes = 'smoke-seed';

DELETE FROM facet_collections
WHERE collection_code = 'FC.FOOD.READY'
  AND notes = 'smoke e2e seed';

DELETE FROM facet_presets
WHERE preset_code IN ('FP.FOOD.READY.DEFAULT', 'FP.FOOD.READY.PIZZA')
  AND notes = 'smoke-seed';

-- 4) Remove smoke category only when nothing references it anymore.
DELETE FROM categories c
WHERE c.code = 'FOOD.READY_MEALS'
  AND c.description = 'Smoke seed category'
  AND NOT EXISTS (
      SELECT 1
      FROM facet_presets fp
      WHERE fp.category_code = c.code
  )
  AND NOT EXISTS (
      SELECT 1
      FROM facet_collections fc
      WHERE fc.category_code = c.code
  )
  AND NOT EXISTS (
      SELECT 1
      FROM catalog_preset_events e
      WHERE e.category_code = c.code
  );

COMMIT;
