# Target Concept Rework Status

## Scope
- Goal: make track target category-centric (`category + optional attributes`) instead of mandatory `brand+model`.
- Primary context: subscriptions/tracks flows (`create/update/dedup/top10`).

## Spectrum Tasks

| ID | Task | Status | Notes |
|---|---|---|---|
| A1 | Product rules and invariants for target model | Completed | Category mandatory for product/category flows adopted in code. |
| A2 | Domain contract redesign (`TrackTargetSpec`) | Completed | Added explicit target spec in domain + core/server DTO with backward compatibility. |
| A3 | UI wizard validation update | Completed | Product no longer requires brand/model; category is required. |
| A4 | Create/Edit quick-track semantics | Completed | Results/Main quick-track now require category and support product-by-attributes. |
| A5 | Server target normalization | Completed | Product now accepts empty match key and requires category. |
| A6 | Server top10 runtime (`missing-match-key`) removal | Completed | Top10 no longer fails product tracks without match key. |
| A7 | Dedup behavior for product without match key | Completed | Duplicate prefilter now category-first with optional match key filter. |
| A8 | Legacy data migration via Flyway | Completed | Added `V19__tracks_target_spec_v2.sql` with backfill + indexes. |
| A9 | API DTO simplification (remove `matchKey` dependency) | Completed | Removed legacy `matchKey/categoryCode/attributes` from create/update API inputs; `target.spec` is primary contract. |
| A10 | Observability/guardrails update | Completed | Added target validation reason codes + logs and guardrails for invalid targets/oversized attributes/runtime-filter bleed. |
| A11 | Full test matrix refresh | Completed | Expanded tracks regression matrix with strict `target.spec` cases, matchKey independence for attr-driven product tracks, and guard-check coverage. |
| A12 | Cleanup of legacy bm-only code paths | Completed | Removed legacy read fallback from critical tracks paths; `target.spec` + v2 target columns are the primary source. |

## Current Wave (Completed)
- `feature/trackeditems`: category mandatory for `PRODUCT`; brand/model optional.
- `feature/results`: quick-track now requires category; chooses `PRODUCT` when attributes exist.
- `feature/main`: subscribe flow now requires category; supports product-by-attributes.
- `server/tracks`: target normalization changed to category-first for `PRODUCT`.
- `domain/core/server`: introduced explicit `TrackTargetSpec(categoryCode, attributes, matchKey)`.
- `server/tracks`: writes/reads new target columns (`target_category_code`, `target_attributes_jsonb`) with strict read precedence from `target.spec` and v2 columns.
- `server/tracks`: target attributes are separated from runtime filters for dedup/search paths.
- `domain/core/server`: removed legacy input fields (`matchKey/categoryCode/attributes`) from track create/update API contracts.
- `server/tracks/top10`: removed `missing-match-key` failure branch.
- `server/tracks/top10`: refresh candidates now carry explicit `targetAttributes`.
- `server/tracks/top10`: product matching uses `matchKey` only when target attributes are empty.
- `server/tracks`: duplicate detection updated for product tracks without match key.
- `server/tracks`: added guardrail validation + observability logs for invalid target payloads and runtime-filter stripping.
- `server/tracks`: added post-migration guard service (startup checks) for empty `target_category_code` and `target_attributes_jsonb` quality.
- `server/db/migration`: added `V19__tracks_target_spec_v2.sql` (schema + backfill + indexes).
- `server/db/checks`: added `tracks_target_spec_post_migration_checks.sql`.
- Added tests:
  - `TrackTargetWizardRequiredIfRulesTest`: product validity without brand/model, category mandatory.
  - `CatalogTracksContractIntegrationTest`: product track without match key works via category+attributes.
  - `CatalogTracksContractIntegrationTest`: product track with attributes ignores mismatching matchKey.
  - `CatalogTracksContractIntegrationTest`: legacy `TrackTarget`/`filters.extra` no longer act as target fallback.
  - `CatalogTracksContractIntegrationTest`: post-migration guard report detects empty category + target attributes quality issues.

## Verification (2026-03-01)
- `./gradlew :server:compileKotlin :core:compileDebugKotlin :domain:compileKotlin --no-daemon "-Pkotlin.incremental=false"` - passed.
- `./gradlew :feature:compileDebugKotlin --no-daemon "-Pkotlin.incremental=false"` - passed.
- `./gradlew :feature:testDebugUnitTest --tests "com.example.shoppingassistant.feature.pages.trackeditems.TrackTargetWizardRequiredIfRulesTest" --no-daemon "-Pkotlin.incremental=false"` - passed.
- `./gradlew :server:test --tests "com.example.shoppingassistant.server.contracts.CatalogTracksContractIntegrationTest" --no-daemon "-Pkotlin.incremental=false"` - passed.

## Next Wave
- Optional DB hardening: add check constraints for non-empty `target_category_code` on `PRODUCT/CATEGORY`.
- Optional rollout cleanup: remove top-level duplicate fields in `TrackTarget` payload and legacy `match_key/category_code` columns after stability window.
