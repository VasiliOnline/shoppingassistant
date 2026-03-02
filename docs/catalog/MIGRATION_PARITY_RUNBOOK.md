# Catalog Migration Parity Runbook

## Goal
Confirm that catalog schema migrations are identical on all target environments and that post-migration data checks are clean.

Required order:

1. `V4__catalog_taxonomy.sql`
2. `V6__track_top10_snapshots.sql`
3. `V9__catalog_constraints.sql`
4. `V10__catalog_stage3_facets.sql`
5. `V11__catalog_category_status.sql`
6. `V12__catalog_temporal_policy.sql`
7. `V13__catalog_preset_observability.sql`
8. `V14__catalog_preset_observability_hardening.sql`
9. `V15__catalog_stage4_contract.sql`
10. `V16__catalog_stage4_runtime_execution.sql`
11. `V17__catalog_stage4_typed_constraints.sql`
12. `V18__offers_geo_coalesce_index.sql`

## Inputs

1. `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
2. Target environment label: `staging` or `prod`
3. Server env: set `APP_ENV=<staging|prod>` and `DB_SCHEMA_AUTOSYNC=false` on managed environments.

## V14 Precheck

Before applying `V14__catalog_preset_observability_hardening.sql`, run:

```sql
SELECT COUNT(*)
FROM catalog_preset_events
WHERE facet_preset_code IS NULL OR BTRIM(facet_preset_code) = '';
```

## Commands

Run per environment:

```bash
psql "host=$DB_HOST port=$DB_PORT dbname=$DB_NAME user=$DB_USER password=$DB_PASSWORD sslmode=require" \
  -f server/src/main/resources/db/checks/catalog_migration_parity_postcheck.sql \
  -o "catalog_migration_postcheck_${ENV}_$(date +%F).txt"
```

Windows (`psql` not in PATH):

```powershell
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" "host=$env:DB_HOST port=$env:DB_PORT dbname=$env:DB_NAME user=$env:DB_USER password=$env:DB_PASSWORD sslmode=require" `
  -f "server/src/main/resources/db/checks/catalog_migration_parity_postcheck.sql" `
  -o "catalog_migration_postcheck_$env:ENV_$(Get-Date -Format yyyy-MM-dd).txt"
```

Domain quality gates (must pass before staging/prod SQL run):

```bash
./gradlew :domain:test \
  --tests "com.example.shoppingassistant.domain.catalog.CatalogModelQualityGateTest" \
  --tests "com.example.shoppingassistant.domain.catalog.CatalogGovernanceGateTest" \
  --tests "com.example.shoppingassistant.domain.catalog.Stage3TopPresetsGateTest" \
  --tests "com.example.shoppingassistant.domain.facet.FacetSchemaValidatorTest"
```

## Pass Criteria

1. `migration_order_status = OK`
2. No `MISSING` column status rows
3. All `invalid_*_count` values are `0`, including:
   - `invalid_preset_event_type_count`
   - `invalid_preset_event_required_fields_count`
   - `invalid_preset_event_position_count`
4. Domain quality gates are green (no stub in top presets, no preset/collection holes, no facet-rule conflicts).
5. `idx_offers_geo_coalesce_gix` exists.

## Report Template

For each environment include:

1. Environment: `<staging|prod>`
2. Executed at: `<UTC timestamp>`
3. Applied order: `<array from SQL output>`
4. Missing columns: `<none | list>`
5. Invalid data counters: `<all zero | list non-zero>`
6. Final status: `<PASS|FAIL>`

## V13-V18 Rollout Evidence

For each environment (`staging`, `prod`) attach:

1. Migration parity output (`catalog_migration_postcheck_*.txt`)
2. Daily observability quality check output (`catalog_preset_observability_checks_*.txt`)
3. Monthly report output (`catalog_preset_monthly_report_*.txt`)
4. Domain test artifact (JUnit XML for quality-gate tests).
