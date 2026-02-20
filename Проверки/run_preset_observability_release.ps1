param(
    [string]$StagingConn = $env:STAGING_DB_CONN,
    [string]$ProdConn = $env:PROD_DB_CONN,
    [string]$PsqlPath = "C:\Program Files\PostgreSQL\17\bin\psql.exe",
    [string]$DockerPsqlContainer = $env:DOCKER_PSQL_CONTAINER,
    [string]$OutDir = "Проверки/out",
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
$parityPostcheckSql = Join-Path $repoRoot "server/src/main/resources/db/checks/catalog_migration_parity_postcheck.sql"
$observabilityChecksSql = Join-Path $repoRoot "server/src/main/resources/db/checks/catalog_preset_observability_checks.sql"
$monthlyReportSql = Join-Path $repoRoot "server/src/main/resources/db/checks/catalog_preset_monthly_report.sql"

foreach ($requiredSql in @($migrationV13Sql, $parityPostcheckSql, $observabilityChecksSql, $monthlyReportSql)) {
    if (-not (Test-Path $requiredSql)) {
        throw "Required SQL file not found: $requiredSql"
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

function Run-ForEnv {
    param(
        [string]$EnvName,
        [string]$Conn
    )

    Invoke-DbScript -EnvName $EnvName -Conn $Conn `
        -SqlPath $migrationV13Sql `
        -OutName "v13_catalog_preset_observability"

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
