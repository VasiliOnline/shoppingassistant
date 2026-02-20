param(
    [string]$StagingConn = $env:STAGING_DB_CONN,
    [string]$ProdConn = $env:PROD_DB_CONN,
    [string]$PsqlPath = "C:\Program Files\PostgreSQL\17\bin\psql.exe",
    [string]$OutDir = "Проверки/out",
    [switch]$RunStagingOnly,
    [switch]$RunProdOnly
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $PsqlPath)) {
    throw "psql not found at '$PsqlPath'"
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

    & $PsqlPath $Conn -w -X -v ON_ERROR_STOP=1 -f $SqlPath -o $outPath
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed for env=$EnvName, sql=$SqlPath"
    }
}

function Invoke-DbScalar {
    param(
        [string]$Conn,
        [string]$Sql
    )

    $value = & $PsqlPath $Conn -w -X -t -A -v ON_ERROR_STOP=1 -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql scalar query failed"
    }
    return ($value | Out-String).Trim()
}

function Run-ForEnv {
    param(
        [string]$EnvName,
        [string]$Conn
    )

    Invoke-DbScript -EnvName $EnvName -Conn $Conn `
        -SqlPath "server/src/main/resources/db/migration/V13__catalog_preset_observability.sql" `
        -OutName "v13_catalog_preset_observability"

    $hasFlywayHistory = (Invoke-DbScalar -Conn $Conn -Sql "SELECT CASE WHEN to_regclass('public.flyway_schema_history') IS NULL THEN '0' ELSE '1' END;") -eq "1"
    if ($hasFlywayHistory) {
        Invoke-DbScript -EnvName $EnvName -Conn $Conn `
            -SqlPath "server/src/main/resources/db/checks/catalog_migration_parity_postcheck.sql" `
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
        -SqlPath "server/src/main/resources/db/checks/catalog_preset_observability_checks.sql" `
        -OutName "catalog_preset_observability_checks"

    Invoke-DbScript -EnvName $EnvName -Conn $Conn `
        -SqlPath "server/src/main/resources/db/checks/catalog_preset_monthly_report.sql" `
        -OutName "catalog_preset_monthly_report"
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
