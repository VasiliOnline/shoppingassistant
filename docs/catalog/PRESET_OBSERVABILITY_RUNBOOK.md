# Preset Observability Runbook

## Goal
Operate production observability for Stage 3 preset performance and controlled A/B rollout.

## Data Sources

1. `catalog_preset_events` (ingested by `/api/offers/observability/preset-events/batch`)
2. `track_top10_snapshots` + `tracks` (zero-results guardrails)
3. `facet_presets` (DEFAULT vs NON_DEFAULT split)

## SQL Sources

1. `server/src/main/resources/db/checks/catalog_preset_observability_checks.sql`
2. `server/src/main/resources/db/checks/catalog_preset_monthly_report.sql`

## Run Cadence

1. Daily 09:10 UTC: observability quality checks
2. Monthly (first business day) 09:30 UTC: default vs non-default report
3. Monthly 10:00 UTC: retention procedure

## Commands

Daily quality checks:

```bash
psql "host=$DB_HOST port=$DB_PORT dbname=$DB_NAME user=$DB_USER password=$DB_PASSWORD sslmode=require" \
  -f server/src/main/resources/db/checks/catalog_preset_observability_checks.sql \
  -o "catalog_preset_observability_checks_${ENV}_$(date +%F).txt"
```

Monthly metrics report:

```bash
psql "host=$DB_HOST port=$DB_PORT dbname=$DB_NAME user=$DB_USER password=$DB_PASSWORD sslmode=require" \
  -f server/src/main/resources/db/checks/catalog_preset_monthly_report.sql \
  -o "catalog_preset_monthly_report_${ENV}_$(date +%F).txt"
```

Retention (keep last 180 days):

```sql
SELECT purge_catalog_preset_events(180);
```

## Operational Thresholds

1. `invalid_required_fields_count = 0`
2. `invalid_event_type_count = 0`
3. `duplicate_idempotency_count = 0`
4. `delayed_ingest_over_6h_count <= 1%` of daily events
5. `click_without_impression_count <= 0.5%` of daily clicks

## A/B Rules For Stub Presets

Candidate presets:

1. `FP.FOOD.READY.DEFAULT`
2. `FP.FOOD.READY.PIZZA`
3. `FP.FOOD.READY.SUSHI`
4. `FP.TECH.PHONES.DEFAULT`

Acceptance criteria (must all pass):

1. Minimum sample: each variant has `>= 1000` impressions per category.
2. Significance: `p < 0.05` for CTR difference (default vs non-default) with two-proportion test.
3. Guardrail: zero-results rate must not worsen by more than `+0.5pp`.
4. Reliability: no unresolved quality-check violations in the same period.

## Release Governance

After acceptance:

1. Remove `Stage 3.0 stub` notes from `taxonomy/stage3/3.0/facet_presets.json`.
2. Update `_registry/data_version_changelog.json` with rollout notes.
3. Attach monthly report output to release notes with `dataVersion`.

## Release Log

`2026-02-20` (`dataVersion = 2.2.6`)

1. Top leaf presets `FP.TECH.LAPTOPS.DEFAULT`, `FP.FOOD.GROCERIES.DEFAULT`, `FP.FOOD.DRINKS.DEFAULT` removed from Stage 3.0 stub status.
2. Full preset-chain closure completed: every preset now has a matching `facet_collection` anchor.
3. CI quality gates hardened for:
   - stub detection on top leaf categories
   - preset/collection integrity
   - conflicting preset rules (duplicate facets, include/exclude overlap, invalid bool shape)

`2026-02-20` (`dataVersion = 2.2.5`)

1. A/B pass recorded for:
   - `FP.FOOD.READY.DEFAULT`
   - `FP.FOOD.READY.PIZZA`
   - `FP.FOOD.READY.SUSHI`
   - `FP.TECH.PHONES.DEFAULT`
2. `Stage 3.0 stub` notes removed from `taxonomy/stage3/3.0/facet_presets.json` for the presets above.
3. Governance artifacts updated:
   - `taxonomy/stage2/2.2/_registry/registry_meta.json`
   - `taxonomy/stage2/2.2/_registry/data_version_changelog.json`
