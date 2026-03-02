param(
    [string]$StagingConn = $env:STAGING_DB_CONN,
    [string]$ProdConn = $env:PROD_DB_CONN,
    [string]$PsqlPath = "C:\Program Files\PostgreSQL\17\bin\psql.exe",
    [string]$DockerPsqlContainer = $env:DOCKER_PSQL_CONTAINER,
    [string]$OutDir = "Проверки/out",
    [int]$Stage4DroppedCount24hMax = 0,
    [switch]$AllowNonZeroDroppedCount24h,
    [switch]$AllowTypedViolations24h,
    [switch]$RunStagingOnly,
    [switch]$RunProdOnly
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($DockerPsqlContainer) -and -not (Test-Path $PsqlPath)) {
    throw "psql not found at '$PsqlPath'"
}

if (-not [string]::IsNullOrWhiteSpace($DockerPsqlContainer)) {
    $containerRunning = (& docker inspect -f "{{.State.Running}}" $DockerPsqlContainer 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $containerRunning -ne "true") {
        throw "Docker container '$DockerPsqlContainer' is not running."
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$migrationV13Sql = Join-Path $repoRoot "server/src/main/resources/db/migration/V13__catalog_preset_observability.sql"
$migrationV14Sql = Join-Path $repoRoot "server/src/main/resources/db/migration/V14__catalog_preset_observability_hardening.sql"
$migrationV15Sql = Join-Path $repoRoot "server/src/main/resources/db/migration/V15__catalog_stage4_contract.sql"
$migrationV16Sql = Join-Path $repoRoot "server/src/main/resources/db/migration/V16__catalog_stage4_runtime_execution.sql"
$migrationV17Sql = Join-Path $repoRoot "server/src/main/resources/db/migration/V17__catalog_stage4_typed_constraints.sql"
$parityPostcheckSql = Join-Path $repoRoot "server/src/main/resources/db/checks/catalog_migration_parity_postcheck.sql"
$observabilityChecksSql = Join-Path $repoRoot "server/src/main/resources/db/checks/catalog_preset_observability_checks.sql"
$monthlyReportSql = Join-Path $repoRoot "server/src/main/resources/db/checks/catalog_preset_monthly_report.sql"
$stage4ContractChecksSql = Join-Path $repoRoot "server/src/main/resources/db/checks/catalog_stage4_contract_checks.sql"
$stage4ImmutableSchemaJsonPath = Join-Path $repoRoot "domain/src/main/resources/taxonomy/stage4/4.0/immutable_attribute_schema.json"
$stage4NormalizationContractJsonPath = Join-Path $repoRoot "domain/src/main/resources/taxonomy/stage4/4.0/normalization_contract.json"
$stage4DedupKeysJsonPath = Join-Path $repoRoot "domain/src/main/resources/taxonomy/stage4/4.0/dedup_keys.json"
$stage4TypedConstraintsJsonPath = Join-Path $repoRoot "domain/src/main/resources/taxonomy/stage4/4.0/typed_constraints.json"

foreach ($requiredPath in @(
    $migrationV13Sql,
    $migrationV14Sql,
    $migrationV15Sql,
    $migrationV16Sql,
    $migrationV17Sql,
    $parityPostcheckSql,
    $observabilityChecksSql,
    $monthlyReportSql,
    $stage4ContractChecksSql,
    $stage4ImmutableSchemaJsonPath,
    $stage4NormalizationContractJsonPath,
    $stage4DedupKeysJsonPath,
    $stage4TypedConstraintsJsonPath
)) {
    if (-not (Test-Path $requiredPath)) {
        throw "Required file not found: $requiredPath"
    }
}

if (-not [System.IO.Path]::IsPathRooted($OutDir)) {
    $OutDir = Join-Path $repoRoot $OutDir
}

if ($RunStagingOnly -and $RunProdOnly) {
    throw "Use either -RunStagingOnly or -RunProdOnly, not both."
}

New-Item -ItemType Directory -Path $OutDir -Force | Out-Null

$dateTag = Get-Date -Format "yyyy-MM-dd"

function Invoke-DbScript {
    param(
        [string]$EnvName,
        [string]$Conn,
        [string]$SqlPath,
        [string]$OutName
    )

    $outPath = Join-Path $OutDir "${OutName}_${EnvName}_${dateTag}.txt"
    Write-Host "[$EnvName] Running $SqlPath -> $outPath"

    if ([string]::IsNullOrWhiteSpace($DockerPsqlContainer)) {
        & $PsqlPath $Conn -w -X -P pager=off -v ON_ERROR_STOP=1 -f $SqlPath -o $outPath
        if ($LASTEXITCODE -ne 0) {
            throw "psql failed for env=$EnvName, sql=$SqlPath"
        }
    } else {
        $sqlContent = Get-Content -Raw $SqlPath
        $output = $sqlContent | docker exec -i $DockerPsqlContainer psql $Conn -w -X -P pager=off -v ON_ERROR_STOP=1 -f - 2>&1
        $output | Set-Content -Path $outPath
        if ($LASTEXITCODE -ne 0) {
            throw "docker psql failed for env=$EnvName, sql=$SqlPath"
        }
    }
}

function Invoke-DbInlineSql {
    param(
        [string]$EnvName,
        [string]$Conn,
        [string]$Sql,
        [string]$OutName
    )

    $outPath = Join-Path $OutDir "${OutName}_${EnvName}_${dateTag}.txt"
    Write-Host "[$EnvName] Running inline SQL ($OutName) -> $outPath"

    if ([string]::IsNullOrWhiteSpace($DockerPsqlContainer)) {
        $Sql | & $PsqlPath $Conn -w -X -P pager=off -v ON_ERROR_STOP=1 -f - -o $outPath
        if ($LASTEXITCODE -ne 0) {
            throw "psql failed for env=$EnvName, inlineSql=$OutName"
        }
    } else {
        $output = $Sql | docker exec -i $DockerPsqlContainer psql $Conn -w -X -P pager=off -v ON_ERROR_STOP=1 -f - 2>&1
        $output | Set-Content -Path $outPath
        if ($LASTEXITCODE -ne 0) {
            throw "docker psql failed for env=$EnvName, inlineSql=$OutName"
        }
    }
}

function Invoke-DbScalar {
    param(
        [string]$Conn,
        [string]$Sql
    )

    if ([string]::IsNullOrWhiteSpace($DockerPsqlContainer)) {
        $value = & $PsqlPath $Conn -w -X -P pager=off -t -A -v ON_ERROR_STOP=1 -c $Sql
    } else {
        $value = & docker exec -i $DockerPsqlContainer psql $Conn -w -X -P pager=off -t -A -v ON_ERROR_STOP=1 -c $Sql
    }
    if ($LASTEXITCODE -ne 0) {
        throw "psql scalar query failed"
    }
    return ($value | Out-String).Trim()
}

function New-Stage4SnapshotUpsertSql {
    $immutableJson = (Get-Content -Raw $stage4ImmutableSchemaJsonPath).Replace("'", "''")
    $normalizationJson = (Get-Content -Raw $stage4NormalizationContractJsonPath).Replace("'", "''")
    $dedupJson = (Get-Content -Raw $stage4DedupKeysJsonPath).Replace("'", "''")
    $typedConstraintsJson = (Get-Content -Raw $stage4TypedConstraintsJsonPath).Replace("'", "''")

    return @"
BEGIN;

TRUNCATE TABLE
    catalog_stage4_contract_meta,
    catalog_stage4_immutable_attributes,
    catalog_stage4_normalization_rules,
    catalog_stage4_dedup_templates,
    catalog_stage4_typed_constraints;

WITH doc AS (
    SELECT '$immutableJson'::jsonb AS j
)
INSERT INTO catalog_stage4_contract_meta (
    stage,
    schema_version,
    stage22_data_version,
    stage22_schema_version,
    stage22_generated_at,
    stage3_version,
    updated_at
)
SELECT
    j->>'stage' AS stage,
    j->>'schemaVersion' AS schema_version,
    j #>> '{generatedFrom,stage22DataVersion}' AS stage22_data_version,
    j #>> '{generatedFrom,stage22SchemaVersion}' AS stage22_schema_version,
    j #>> '{generatedFrom,stage22GeneratedAt}' AS stage22_generated_at,
    j #>> '{generatedFrom,stage3Version}' AS stage3_version,
    (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint AS updated_at
FROM doc;

WITH doc AS (
    SELECT '$immutableJson'::jsonb AS j
)
INSERT INTO catalog_stage4_immutable_attributes (
    attribute_code,
    value_type,
    value_set_type,
    unit,
    is_identity,
    is_facet,
    normalization,
    dictionary_required,
    immutable_fingerprint
)
SELECT
    item->>'attributeCode' AS attribute_code,
    item->>'valueType' AS value_type,
    item->>'valueSetType' AS value_set_type,
    NULLIF(item->>'unit', '') AS unit,
    COALESCE((item->>'isIdentity')::boolean, false) AS is_identity,
    COALESCE((item->>'isFacet')::boolean, false) AS is_facet,
    NULLIF(item->>'normalization', '') AS normalization,
    COALESCE((item->>'dictionaryRequired')::boolean, false) AS dictionary_required,
    item->>'immutableFingerprint' AS immutable_fingerprint
FROM doc
CROSS JOIN LATERAL jsonb_array_elements(doc.j->'attributes') AS item;

WITH doc AS (
    SELECT '$normalizationJson'::jsonb AS j
)
INSERT INTO catalog_stage4_normalization_rules (
    attribute_code,
    normalization,
    value_set_type,
    dictionary_backed,
    accepts_free_text,
    canonical_source,
    dedup_token_mode
)
SELECT
    item->>'attributeCode' AS attribute_code,
    item->>'normalization' AS normalization,
    item->>'valueSetType' AS value_set_type,
    COALESCE((item->>'dictionaryBacked')::boolean, false) AS dictionary_backed,
    COALESCE((item->>'acceptsFreeText')::boolean, false) AS accepts_free_text,
    item->>'canonicalSource' AS canonical_source,
    item->>'dedupTokenMode' AS dedup_token_mode
FROM doc
CROSS JOIN LATERAL jsonb_array_elements(doc.j->'rules') AS item;

WITH doc AS (
    SELECT '$dedupJson'::jsonb AS j
)
INSERT INTO catalog_stage4_dedup_templates (
    entity,
    template_expr,
    fields,
    description
)
SELECT
    item->>'entity' AS entity,
    item->>'template' AS template_expr,
    COALESCE(item->'fields', '[]'::jsonb) AS fields,
    item->>'description' AS description
FROM doc
CROSS JOIN LATERAL jsonb_array_elements(doc.j->'templates') AS item;

WITH doc AS (
    SELECT '$typedConstraintsJson'::jsonb AS j
)
INSERT INTO catalog_stage4_typed_constraints (
    attribute_code,
    value_type,
    enum_only,
    expected_unit,
    regex_pattern,
    min_value,
    max_value,
    required_if,
    updated_at
)
SELECT
    item->>'attributeCode' AS attribute_code,
    item->>'valueType' AS value_type,
    COALESCE((item->>'enumOnly')::boolean, false) AS enum_only,
    NULLIF(item->>'unit', '') AS expected_unit,
    NULLIF(item->>'regex', '') AS regex_pattern,
    CASE WHEN item ? 'minValue' AND item->>'minValue' IS NOT NULL THEN (item->>'minValue')::double precision ELSE NULL END AS min_value,
    CASE WHEN item ? 'maxValue' AND item->>'maxValue' IS NOT NULL THEN (item->>'maxValue')::double precision ELSE NULL END AS max_value,
    COALESCE(item->'requiredIf', '[]'::jsonb) AS required_if,
    (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint AS updated_at
FROM doc
CROSS JOIN LATERAL jsonb_array_elements(doc.j->'constraints') AS item;

COMMIT;
"@
}

function New-Stage4GateViolationSql {
    return @"
WITH presence_metrics AS (
    SELECT
        CASE WHEN to_regclass('public.catalog_stage4_contract_meta') IS NULL THEN 1 ELSE 0 END AS missing_stage4_meta_table_count,
        CASE WHEN to_regclass('public.catalog_stage4_immutable_attributes') IS NULL THEN 1 ELSE 0 END AS missing_stage4_immutable_table_count,
        CASE WHEN to_regclass('public.catalog_stage4_normalization_rules') IS NULL THEN 1 ELSE 0 END AS missing_stage4_normalization_table_count,
        CASE WHEN to_regclass('public.catalog_stage4_dedup_templates') IS NULL THEN 1 ELSE 0 END AS missing_stage4_dedup_table_count,
        CASE WHEN to_regclass('public.catalog_stage4_typed_constraints') IS NULL THEN 1 ELSE 0 END AS missing_stage4_typed_constraints_table_count,
        CASE WHEN to_regclass('public.catalog_stage4_execution_metrics') IS NULL THEN 1 ELSE 0 END AS missing_stage4_execution_metrics_table_count
),
meta_metrics AS (
    SELECT
        CASE WHEN COUNT(*) FILTER (WHERE stage = '4.0') = 1 THEN 0 ELSE 1 END AS stage4_meta_row_mismatch_count
    FROM catalog_stage4_contract_meta
),
stage2_defs AS (
    SELECT
        ad.code AS attribute_code,
        CASE
            WHEN ad.data_type = 'ENUM' THEN 'ENUM'
            WHEN ad.data_type IN ('INT', 'DECIMAL') THEN 'NUMBER'
            WHEN ad.data_type = 'BOOL' THEN 'BOOLEAN'
            ELSE 'STRING'
        END AS expected_value_type,
        EXISTS (
            SELECT 1
            FROM attribute_value_dict d
            WHERE d.attribute_code = ad.code
        ) AS dict_exists
    FROM attribute_defs ad
),
stage4_immutable AS (
    SELECT
        attribute_code,
        value_type,
        value_set_type,
        unit,
        dictionary_required
    FROM catalog_stage4_immutable_attributes
),
immutable_metrics AS (
    SELECT
        (SELECT COUNT(*) FROM stage2_defs s2 LEFT JOIN stage4_immutable s4 ON s4.attribute_code = s2.attribute_code WHERE s4.attribute_code IS NULL)
            AS stage4_immutable_missing_from_stage2_count,
        (SELECT COUNT(*) FROM stage4_immutable s4 LEFT JOIN stage2_defs s2 ON s2.attribute_code = s4.attribute_code WHERE s2.attribute_code IS NULL)
            AS stage4_immutable_extra_vs_stage2_count,
        (SELECT COUNT(*) FROM stage2_defs s2 JOIN stage4_immutable s4 ON s4.attribute_code = s2.attribute_code WHERE s4.value_type <> s2.expected_value_type)
            AS stage4_immutable_value_type_mismatch_count,
        (SELECT COUNT(*) FROM stage2_defs s2 JOIN stage4_immutable s4 ON s4.attribute_code = s2.attribute_code
            WHERE s4.dictionary_required <> (s2.expected_value_type = 'ENUM' AND s2.dict_exists))
            AS stage4_immutable_dictionary_required_mismatch_count
),
stage4_norm AS (
    SELECT
        attribute_code,
        value_set_type,
        dictionary_backed,
        accepts_free_text,
        canonical_source,
        dedup_token_mode
    FROM catalog_stage4_normalization_rules
),
normalization_metrics AS (
    SELECT
        (SELECT COUNT(*) FROM stage2_defs s2 LEFT JOIN stage4_norm n ON n.attribute_code = s2.attribute_code WHERE n.attribute_code IS NULL)
            AS stage4_normalization_missing_from_stage2_count,
        (SELECT COUNT(*) FROM stage4_norm n LEFT JOIN stage2_defs s2 ON s2.attribute_code = n.attribute_code WHERE s2.attribute_code IS NULL)
            AS stage4_normalization_extra_vs_stage2_count,
        (SELECT COUNT(*) FROM stage4_norm n JOIN stage4_immutable i ON i.attribute_code = n.attribute_code
            WHERE n.value_set_type <> i.value_set_type)
            AS stage4_normalization_value_set_mismatch_count,
        (SELECT COUNT(*) FROM stage4_norm n JOIN stage4_immutable i ON i.attribute_code = n.attribute_code
            WHERE n.dictionary_backed <> i.dictionary_required)
            AS stage4_normalization_dictionary_backed_mismatch_count,
        (SELECT COUNT(*) FROM stage4_norm n
            WHERE (n.dictionary_backed AND n.accepts_free_text)
               OR ((NOT n.dictionary_backed) AND (NOT n.accepts_free_text)))
            AS stage4_normalization_accepts_free_text_mismatch_count,
        (SELECT COUNT(*) FROM stage4_norm n
            WHERE (n.dictionary_backed AND (n.canonical_source <> 'taxonomy/stage2/2.2/_registry/value_dictionaries.json' OR n.dedup_token_mode <> 'VALUE_CODE'))
               OR ((NOT n.dictionary_backed) AND (n.canonical_source <> 'inline' OR n.dedup_token_mode <> 'NORMALIZED_TEXT')))
            AS stage4_normalization_canonical_dedup_mode_mismatch_count
),
facet_metrics AS (
    SELECT
        COUNT(*) AS stage4_missing_stage3_facet_keys_count
    FROM (
        SELECT fd.facet_key
        FROM facet_definitions fd
        WHERE fd.source <> 'DERIVED'
          AND fd.facet_key <> 'price'
    ) stage3_facets
    LEFT JOIN catalog_stage4_immutable_attributes s4
        ON s4.attribute_code = stage3_facets.facet_key
    WHERE s4.attribute_code IS NULL
),
expected_templates(entity, template_expr, fields_json) AS (
    VALUES
        ('ATTRIBUTE_SCHEMA', '{attributeCode}', '["attributeCode"]'::jsonb),
        ('DICTIONARY_VALUE', '{attributeCode}|{valueCode}', '["attributeCode","valueCode"]'::jsonb),
        ('CATEGORY_PROFILE_ATTRIBUTE', '{categoryCode}|{attributeCode}', '["categoryCode","attributeCode"]'::jsonb),
        ('FACET_DEFINITION', '{facetKey}', '["facetKey"]'::jsonb),
        ('FACET_PRESET', '{presetCode}', '["presetCode"]'::jsonb),
        ('FACET_COLLECTION', '{collectionCode}', '["collectionCode"]'::jsonb),
        ('CATEGORY_IDENTITY_SIGNATURE', '{categoryCode}|{identityAttributesHash}', '["categoryCode","identityAttributesHash"]'::jsonb)
),
actual_templates AS (
    SELECT
        entity,
        template_expr,
        fields
    FROM catalog_stage4_dedup_templates
),
dedup_metrics AS (
    SELECT
        (SELECT COUNT(*) FROM expected_templates e LEFT JOIN actual_templates a ON a.entity = e.entity WHERE a.entity IS NULL)
            AS stage4_dedup_missing_templates_count,
        (SELECT COUNT(*) FROM actual_templates a LEFT JOIN expected_templates e ON e.entity = a.entity WHERE e.entity IS NULL)
            AS stage4_dedup_extra_templates_count,
        (SELECT COUNT(*) FROM expected_templates e JOIN actual_templates a ON a.entity = e.entity
            WHERE a.template_expr <> e.template_expr OR a.fields <> e.fields_json)
            AS stage4_dedup_template_mismatch_count
),
typed_constraints AS (
    SELECT
        LOWER(attribute_code) AS attribute_code,
        value_type,
        expected_unit,
        min_value,
        max_value,
        required_if
    FROM catalog_stage4_typed_constraints
),
typed_required_if_entries AS (
    SELECT
        tc.attribute_code AS required_attribute_code,
        rule_item ->> 'categoryCode' AS category_code,
        condition_item ->> 'attributeCode' AS condition_attribute_code
    FROM typed_constraints tc
    CROSS JOIN LATERAL jsonb_array_elements(COALESCE(tc.required_if, '[]'::jsonb)) AS rule_item
    CROSS JOIN LATERAL jsonb_array_elements(COALESCE(rule_item -> 'whenAll', '[]'::jsonb)) AS condition_item
),
typed_metrics AS (
    SELECT
        (SELECT COUNT(*) FROM typed_constraints tc LEFT JOIN stage4_immutable i ON i.attribute_code = tc.attribute_code WHERE i.attribute_code IS NULL)
            AS stage4_typed_constraints_unknown_attribute_count,
        (SELECT COUNT(*) FROM stage4_immutable i LEFT JOIN typed_constraints tc ON tc.attribute_code = i.attribute_code WHERE tc.attribute_code IS NULL)
            AS stage4_typed_constraints_missing_from_immutable_count,
        (SELECT COUNT(*) FROM typed_constraints tc JOIN stage4_immutable i ON i.attribute_code = tc.attribute_code WHERE tc.value_type <> i.value_type)
            AS stage4_typed_constraints_value_type_mismatch_count,
        (SELECT COUNT(*) FROM typed_constraints tc JOIN stage4_immutable i ON i.attribute_code = tc.attribute_code
            WHERE COALESCE(tc.expected_unit, '') <> COALESCE(i.unit, ''))
            AS stage4_typed_constraints_unit_mismatch_count,
        (SELECT COUNT(*) FROM typed_constraints tc
            WHERE tc.min_value IS NOT NULL AND tc.max_value IS NOT NULL AND tc.min_value > tc.max_value)
            AS stage4_typed_constraints_invalid_range_count,
        (SELECT COUNT(*) FROM typed_required_if_entries rie
            LEFT JOIN categories c ON c.code = rie.category_code
            WHERE rie.category_code IS NULL OR btrim(rie.category_code) = '' OR c.code IS NULL)
            AS stage4_typed_constraints_required_if_unknown_category_count,
        (SELECT COUNT(*) FROM typed_required_if_entries rie
            LEFT JOIN stage4_immutable i ON i.attribute_code = LOWER(rie.condition_attribute_code)
            WHERE rie.condition_attribute_code IS NULL OR btrim(rie.condition_attribute_code) = '' OR i.attribute_code IS NULL)
            AS stage4_typed_constraints_required_if_unknown_attribute_count
)
SELECT (
    presence_metrics.missing_stage4_meta_table_count +
    presence_metrics.missing_stage4_immutable_table_count +
    presence_metrics.missing_stage4_normalization_table_count +
    presence_metrics.missing_stage4_dedup_table_count +
    presence_metrics.missing_stage4_typed_constraints_table_count +
    presence_metrics.missing_stage4_execution_metrics_table_count +
    meta_metrics.stage4_meta_row_mismatch_count +
    immutable_metrics.stage4_immutable_missing_from_stage2_count +
    immutable_metrics.stage4_immutable_extra_vs_stage2_count +
    immutable_metrics.stage4_immutable_value_type_mismatch_count +
    immutable_metrics.stage4_immutable_dictionary_required_mismatch_count +
    normalization_metrics.stage4_normalization_missing_from_stage2_count +
    normalization_metrics.stage4_normalization_extra_vs_stage2_count +
    normalization_metrics.stage4_normalization_value_set_mismatch_count +
    normalization_metrics.stage4_normalization_dictionary_backed_mismatch_count +
    normalization_metrics.stage4_normalization_accepts_free_text_mismatch_count +
    normalization_metrics.stage4_normalization_canonical_dedup_mode_mismatch_count +
    facet_metrics.stage4_missing_stage3_facet_keys_count +
    dedup_metrics.stage4_dedup_missing_templates_count +
    dedup_metrics.stage4_dedup_extra_templates_count +
    dedup_metrics.stage4_dedup_template_mismatch_count +
    typed_metrics.stage4_typed_constraints_unknown_attribute_count +
    typed_metrics.stage4_typed_constraints_missing_from_immutable_count +
    typed_metrics.stage4_typed_constraints_value_type_mismatch_count +
    typed_metrics.stage4_typed_constraints_unit_mismatch_count +
    typed_metrics.stage4_typed_constraints_invalid_range_count +
    typed_metrics.stage4_typed_constraints_required_if_unknown_category_count +
    typed_metrics.stage4_typed_constraints_required_if_unknown_attribute_count
)::text AS stage4_contract_gate_violations
FROM presence_metrics
CROSS JOIN meta_metrics
CROSS JOIN immutable_metrics
CROSS JOIN normalization_metrics
CROSS JOIN facet_metrics
CROSS JOIN dedup_metrics
CROSS JOIN typed_metrics;
"@
}

function Assert-Stage4ContractGate {
    param(
        [string]$EnvName,
        [string]$Conn
    )

    $violationText = Invoke-DbScalar -Conn $Conn -Sql (New-Stage4GateViolationSql)
    [int]$violationCount = 0
    if (-not [int]::TryParse($violationText, [ref]$violationCount)) {
        throw "Failed to parse Stage4 gate violation count for env=$EnvName. Raw value: '$violationText'"
    }

    if ($violationCount -gt 0) {
        $reportPath = Join-Path $OutDir "catalog_stage4_contract_checks_${EnvName}_${dateTag}.txt"
        throw "Stage4 contract hard-fail for env=${EnvName}: detected $violationCount non-zero mismatch/missing/extra metrics. See $reportPath"
    }

    Write-Host "[$EnvName] Stage4 hard gate passed (all mismatch/missing/extra metrics are zero)."
}

function New-Stage4DailyGateSnapshotSql {
    return @"
WITH recent_metrics AS (
    SELECT *
    FROM catalog_stage4_execution_metrics
    WHERE metric_date >= CURRENT_DATE - 1
      AND stream IN ('OFFERS_INGEST', 'PRESET_EVENTS_INGEST')
),
recent_reasons AS (
    SELECT
        rc.reason_code
    FROM recent_metrics m
    CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(m.reason_codes, '[]'::jsonb)) AS rc(reason_code)
)
SELECT
    COALESCE((SELECT SUM(normalized_count) FROM recent_metrics), 0) AS stage4_ingest_normalized_count_24h,
    COALESCE((SELECT SUM(dropped_count) FROM recent_metrics), 0) AS stage4_ingest_dropped_count_24h,
    COALESCE((SELECT SUM(logical_dedup_count) FROM recent_metrics), 0) AS stage4_ingest_logical_dedup_count_24h,
    COALESCE((SELECT SUM(unknown_attribute_count) FROM recent_metrics), 0) AS stage4_ingest_unknown_attribute_count_24h,
    COALESCE((SELECT COUNT(*) FROM recent_reasons WHERE reason_code LIKE 'OUT_OF_RANGE:%'), 0) AS out_of_range_count_24h,
    COALESCE((SELECT COUNT(*) FROM recent_reasons WHERE reason_code LIKE 'UNIT_MISMATCH:%'), 0) AS unit_mismatch_count_24h,
    COALESCE((SELECT COUNT(*) FROM recent_reasons WHERE reason_code LIKE 'PATTERN_MISMATCH:%'), 0) AS pattern_mismatch_count_24h;
"@
}

function Assert-Stage4DailyGate {
    param(
        [string]$EnvName,
        [string]$Conn
    )

    $unknownText = Invoke-DbScalar -Conn $Conn -Sql @"
SELECT COALESCE(SUM(unknown_attribute_count), 0)::text
FROM catalog_stage4_execution_metrics
WHERE metric_date >= CURRENT_DATE - 1
  AND stream IN ('OFFERS_INGEST', 'PRESET_EVENTS_INGEST');
"@

    [int]$unknownCount = 0
    if (-not [int]::TryParse($unknownText, [ref]$unknownCount)) {
        throw "Failed to parse stage4_ingest_unknown_attribute_count_24h for env=$EnvName. Raw value: '$unknownText'"
    }
    if ($unknownCount -gt 0) {
        throw "Stage4 daily gate hard-fail for env=${EnvName}: stage4_ingest_unknown_attribute_count_24h=$unknownCount (must be 0)."
    }

    $droppedText = Invoke-DbScalar -Conn $Conn -Sql @"
SELECT COALESCE(SUM(dropped_count), 0)::text
FROM catalog_stage4_execution_metrics
WHERE metric_date >= CURRENT_DATE - 1
  AND stream IN ('OFFERS_INGEST', 'PRESET_EVENTS_INGEST');
"@

    [int]$droppedCount = 0
    if (-not [int]::TryParse($droppedText, [ref]$droppedCount)) {
        throw "Failed to parse stage4_ingest_dropped_count_24h for env=$EnvName. Raw value: '$droppedText'"
    }

    if ($droppedCount -gt $Stage4DroppedCount24hMax) {
        if ($AllowNonZeroDroppedCount24h) {
            Write-Warning "[$EnvName] stage4_ingest_dropped_count_24h=$droppedCount exceeds threshold $Stage4DroppedCount24hMax, but override is enabled."
        } else {
            throw "Stage4 daily gate fail for env=${EnvName}: stage4_ingest_dropped_count_24h=$droppedCount exceeds threshold $Stage4DroppedCount24hMax."
        }
    }

    $outOfRangeText = Invoke-DbScalar -Conn $Conn -Sql @"
SELECT COUNT(*)::text
FROM catalog_stage4_execution_metrics m
CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(m.reason_codes, '[]'::jsonb)) AS rc(reason_code)
WHERE m.metric_date >= CURRENT_DATE - 1
  AND m.stream IN ('OFFERS_INGEST', 'PRESET_EVENTS_INGEST')
  AND rc.reason_code LIKE 'OUT_OF_RANGE:%';
"@
    $unitMismatchText = Invoke-DbScalar -Conn $Conn -Sql @"
SELECT COUNT(*)::text
FROM catalog_stage4_execution_metrics m
CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(m.reason_codes, '[]'::jsonb)) AS rc(reason_code)
WHERE m.metric_date >= CURRENT_DATE - 1
  AND m.stream IN ('OFFERS_INGEST', 'PRESET_EVENTS_INGEST')
  AND rc.reason_code LIKE 'UNIT_MISMATCH:%';
"@
    $patternMismatchText = Invoke-DbScalar -Conn $Conn -Sql @"
SELECT COUNT(*)::text
FROM catalog_stage4_execution_metrics m
CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(m.reason_codes, '[]'::jsonb)) AS rc(reason_code)
WHERE m.metric_date >= CURRENT_DATE - 1
  AND m.stream IN ('OFFERS_INGEST', 'PRESET_EVENTS_INGEST')
  AND rc.reason_code LIKE 'PATTERN_MISMATCH:%';
"@

    [int]$outOfRangeCount = 0
    [int]$unitMismatchCount = 0
    [int]$patternMismatchCount = 0
    if (-not [int]::TryParse($outOfRangeText, [ref]$outOfRangeCount)) {
        throw "Failed to parse OUT_OF_RANGE count for env=$EnvName. Raw value: '$outOfRangeText'"
    }
    if (-not [int]::TryParse($unitMismatchText, [ref]$unitMismatchCount)) {
        throw "Failed to parse UNIT_MISMATCH count for env=$EnvName. Raw value: '$unitMismatchText'"
    }
    if (-not [int]::TryParse($patternMismatchText, [ref]$patternMismatchCount)) {
        throw "Failed to parse PATTERN_MISMATCH count for env=$EnvName. Raw value: '$patternMismatchText'"
    }

    $typedViolationsTotal = $outOfRangeCount + $unitMismatchCount + $patternMismatchCount
    if ($typedViolationsTotal -gt 0) {
        if ($AllowTypedViolations24h) {
            Write-Warning "[$EnvName] typed Stage4 violations in 24h: OUT_OF_RANGE=$outOfRangeCount UNIT_MISMATCH=$unitMismatchCount PATTERN_MISMATCH=$patternMismatchCount (override enabled)."
        } else {
            throw "Stage4 daily gate fail for env=${EnvName}: typed violations in 24h are non-zero (OUT_OF_RANGE=$outOfRangeCount, UNIT_MISMATCH=$unitMismatchCount, PATTERN_MISMATCH=$patternMismatchCount)."
        }
    }

    Write-Host "[$EnvName] Stage4 daily gate passed (unknown=$unknownCount, dropped=$droppedCount, droppedThreshold=$Stage4DroppedCount24hMax, outOfRange=$outOfRangeCount, unitMismatch=$unitMismatchCount, patternMismatch=$patternMismatchCount)."
}

function Run-ForEnv {
    param(
        [string]$EnvName,
        [string]$Conn
    )

    Invoke-DbScript -EnvName $EnvName -Conn $Conn `
        -SqlPath $migrationV13Sql `
        -OutName "v13_catalog_preset_observability"

    Invoke-DbScript -EnvName $EnvName -Conn $Conn `
        -SqlPath $migrationV14Sql `
        -OutName "v14_catalog_preset_observability_hardening"

    Invoke-DbScript -EnvName $EnvName -Conn $Conn `
        -SqlPath $migrationV15Sql `
        -OutName "v15_catalog_stage4_contract"

    Invoke-DbScript -EnvName $EnvName -Conn $Conn `
        -SqlPath $migrationV16Sql `
        -OutName "v16_catalog_stage4_runtime_execution"

    Invoke-DbScript -EnvName $EnvName -Conn $Conn `
        -SqlPath $migrationV17Sql `
        -OutName "v17_catalog_stage4_typed_constraints"

    Invoke-DbInlineSql -EnvName $EnvName -Conn $Conn `
        -Sql (New-Stage4SnapshotUpsertSql) `
        -OutName "stage4_contract_upsert"

    $hasFlywayHistory = (Invoke-DbScalar -Conn $Conn -Sql "SELECT CASE WHEN to_regclass('public.flyway_schema_history') IS NULL THEN '0' ELSE '1' END;") -eq "1"
    if ($hasFlywayHistory) {
        Invoke-DbScript -EnvName $EnvName -Conn $Conn `
            -SqlPath $parityPostcheckSql `
            -OutName "catalog_migration_postcheck"
    } else {
        $skipPath = Join-Path $OutDir "catalog_migration_postcheck_${EnvName}_${dateTag}.txt"
        @(
            "SKIPPED",
            "Reason: flyway_schema_history table is missing in this environment.",
            "Action: run parity postcheck on managed staging/prod where Flyway metadata exists."
        ) | Set-Content -Path $skipPath
        Write-Host "[$EnvName] Skipping catalog_migration_parity_postcheck.sql (flyway_schema_history not found)."
    }

    Invoke-DbScript -EnvName $EnvName -Conn $Conn `
        -SqlPath $observabilityChecksSql `
        -OutName "catalog_preset_observability_checks"

    Invoke-DbScript -EnvName $EnvName -Conn $Conn `
        -SqlPath $monthlyReportSql `
        -OutName "catalog_preset_monthly_report"

    Invoke-DbScript -EnvName $EnvName -Conn $Conn `
        -SqlPath $stage4ContractChecksSql `
        -OutName "catalog_stage4_contract_checks"

    Assert-Stage4ContractGate -EnvName $EnvName -Conn $Conn

    Invoke-DbInlineSql -EnvName $EnvName -Conn $Conn `
        -Sql (New-Stage4DailyGateSnapshotSql) `
        -OutName "catalog_stage4_daily_gate"

    Assert-Stage4DailyGate -EnvName $EnvName -Conn $Conn
}

if (-not $RunProdOnly) {
    if ([string]::IsNullOrWhiteSpace($StagingConn)) {
        throw "Staging connection is missing. Pass -StagingConn or set STAGING_DB_CONN."
    }
    Run-ForEnv -EnvName "staging" -Conn $StagingConn
}

if (-not $RunStagingOnly) {
    if ([string]::IsNullOrWhiteSpace($ProdConn)) {
        throw "Prod connection is missing. Pass -ProdConn or set PROD_DB_CONN."
    }
    Run-ForEnv -EnvName "prod" -Conn $ProdConn
}

Write-Host "Completed. Reports are in '$OutDir'."
