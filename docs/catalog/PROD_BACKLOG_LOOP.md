# Catalog Model Closed-Loop Backlog

## Goal
Produce a recurring backlog from production signals and enforce SLA on fixes.

Data sources:

1. Zero-results snapshots from `track_top10_snapshots`
2. Unknown offer attribute keys (`offers.attributes` vs `category_attributes`)
3. Normalization conflicts for dictionary-backed attributes (`attribute_value_dict`)
4. Stage 4 closed-set unknown values and incompatible value types (`catalog_stage4_*` + `offers.attributes`)
5. Stage 4 contract drift (`attribute_defs`/`facet_*` vs `catalog_stage4_*`)
6. Stage 4 typed violations (`OUT_OF_RANGE`, `UNIT_MISMATCH`, `PATTERN_MISMATCH`)
7. Stage 4 runtime execution metrics (`catalog_stage4_execution_metrics`)

SQL source:

1. `server/src/main/resources/db/checks/catalog_model_backlog.sql`
2. `server/src/main/resources/db/checks/catalog_stage4_contract_checks.sql`

## Run Cadence

1. Daily at 09:00 UTC for operational triage
2. Weekly summary on Monday 09:30 UTC

## SLA

1. `ZERO_RESULTS`: 3 calendar days
2. `UNKNOWN_ATTRIBUTE`: 7 calendar days
3. `NORMALIZATION_CONFLICT`: 7 calendar days
4. `STAGE4_UNKNOWN_CLOSED_SET_VALUE`: 3 calendar days
5. `STAGE4_INCOMPATIBLE_VALUE`: 3 calendar days
6. `OUT_OF_RANGE`: 3 calendar days
7. `UNIT_MISMATCH`: 3 calendar days
8. `PATTERN_MISMATCH`: 3 calendar days
9. `STAGE4_CONTRACT_DRIFT`: 1 calendar day

## Execution

```bash
psql "host=$DB_HOST port=$DB_PORT dbname=$DB_NAME user=$DB_USER password=$DB_PASSWORD sslmode=require" \
  -f server/src/main/resources/db/checks/catalog_model_backlog.sql \
  -o "catalog_backlog_${ENV}_$(date +%F).txt"
```

```bash
psql "host=$DB_HOST port=$DB_PORT dbname=$DB_NAME user=$DB_USER password=$DB_PASSWORD sslmode=require" \
  -f server/src/main/resources/db/checks/catalog_stage4_contract_checks.sql \
  -o "catalog_stage4_contract_checks_${ENV}_$(date +%F).txt"
```

Stage 4 expected gate:

1. All `stage4_*_mismatch_count` metrics must be `0`.
2. All `stage4_*_missing_*_count` and `stage4_*_extra_*_count` metrics must be `0`.
3. `stage4_meta_rows_count` must be `1` for `stage='4.0'`.
4. `missing_stage4_execution_metrics_table_count` must be `0`.
5. `stage4_ingest_unknown_attribute_count_24h` must be `0`.
6. `stage4_ingest_dropped_count_24h` should trend to `0`; non-zero requires explicit backlog ticket with reason codes.
7. `out_of_range_count_24h`, `unit_mismatch_count_24h`, `pattern_mismatch_count_24h` must be `0` (or explicit override in emergency rollout).

## Automation

Daily gate is enforced by `Проверки/run_preset_observability_release.ps1`:

1. Hard-fail on any Stage 4 contract mismatch/missing/extra metric (`Assert-Stage4ContractGate`).
2. Hard-fail on `stage4_ingest_unknown_attribute_count_24h > 0`.
3. Enforce `stage4_ingest_dropped_count_24h <= Stage4DroppedCount24hMax` (default `0`).
4. Hard-fail on typed violations in last 24h (`OUT_OF_RANGE`, `UNIT_MISMATCH`, `PATTERN_MISMATCH`) unless override is enabled.
5. Persist daily gate snapshot report: `catalog_stage4_daily_gate_<env>_<date>.txt`.

Override for emergency rollout only:

```powershell
.\Проверки\run_preset_observability_release.ps1 `
  -RunStagingOnly `
  -Stage4DroppedCount24hMax 5 `
  -AllowNonZeroDroppedCount24h `
  -AllowTypedViolations24h
```

## Runtime Backfill

Run historical backfill through Stage 4 runtime (offers + preset events):

```bash
STAGE4_RUNTIME_BACKFILL_ON_STARTUP=true \
STAGE4_RUNTIME_BACKFILL_EXIT_AFTER_RUN=true \
STAGE4_RUNTIME_BACKFILL_BATCH_SIZE=500 \
./gradlew :server:run
```

Backfill writes execution metrics into `catalog_stage4_execution_metrics`:

1. `OFFERS_BACKFILL`
2. `PRESET_EVENTS_BACKFILL`

## Governance Hooks

1. Every resolved backlog item must reference:
   - `dataVersion`
   - changed files
   - rollout date
2. Monthly release must update:
   - `taxonomy/stage2/2.2/_registry/registry_meta.json`
   - `taxonomy/stage2/2.2/_registry/data_version_changelog.json`
   - `taxonomy/stage2/2.2/_registry/data_release_policy.json`
   - `taxonomy/stage4/4.0/immutable_attribute_schema.json`
   - `taxonomy/stage4/4.0/normalization_contract.json`
   - `taxonomy/stage4/4.0/dedup_keys.json`
3. Preset experiments and stub-retirement decisions must include:
   - `docs/catalog/PRESET_OBSERVABILITY_RUNBOOK.md` report outputs
   - `server/src/main/resources/db/checks/catalog_preset_monthly_report.sql` attachment
4. Stage 4 contract updates must include:
   - `taxonomy/stage4/4.0/typed_constraints.json`
