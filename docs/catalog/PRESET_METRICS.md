# Stage3 Preset Metrics

## Objective
Measure whether non-default presets improve discovery quality for top leaf categories.

Primary metrics:

1. `ctr_proxy` = interactions / impressions
2. `conversion_proxy` = target hits / impressions

## Current data availability

1. Explicit preset events: `catalog_preset_events` (`IMPRESSION`, `CLICK`, `CONVERSION`)
2. Guardrail source: `track_top10_snapshots` (zero-results rate by category)
3. Schema checks: `catalog_preset_observability_checks.sql`
4. Monthly release report: `catalog_preset_monthly_report.sql`

## Metric formulas

1. `ctr` = `clicks / impressions`
2. `cvr` = `conversions / impressions`
3. Variant split:
   - `DEFAULT`: `facet_preset_code` ends with `.DEFAULT`
   - `NON_DEFAULT`: all other preset codes

## Operational rule

1. For each monthly `dataVersion` release, compare default vs non-default presets for top leaf categories and attach report to release notes.
2. Treat results as valid only if observability quality checks pass (`invalid_* = 0`, dedup and lag thresholds within limits).
3. Use `docs/catalog/PRESET_OBSERVABILITY_RUNBOOK.md` for full operational flow and A/B acceptance criteria.
