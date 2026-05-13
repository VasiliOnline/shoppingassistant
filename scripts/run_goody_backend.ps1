param(
    [string]$ShoppingAssistantDir = "C:\Users\Asus\Desktop\Shoppingassistant",
    [string]$ComposeDir = "C:\Users\Asus\Desktop\archive_server",
    [string]$DbPassword = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Step([string]$message) {
    Write-Host ""
    Write-Host "==> $message" -ForegroundColor Cyan
}

function Ensure-Command([string]$name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        throw "Command '$name' not found in PATH."
    }
}

function Wait-HttpOk([string]$url, [int]$maxAttempts = 60, [int]$sleepSeconds = 2) {
    for ($i = 1; $i -le $maxAttempts; $i++) {
        try {
            $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
            if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 300) {
                return $resp
            }
        } catch {
            # continue
        }
        Start-Sleep -Seconds $sleepSeconds
    }
    throw "Timeout waiting for $url"
}

function Invoke-Checked([scriptblock]$block, [string]$errorMessage) {
    & $block
    if ($LASTEXITCODE -ne 0) {
        throw "$errorMessage (exit code $LASTEXITCODE)"
    }
}

function Invoke-DbSql([string]$sql, [string]$errorMessage) {
    $sql | docker exec -i -e "PGPASSWORD=$env:SA_DB_PASSWORD" goody-postgres `
        psql -U Boss -d shoppingassistant -v ON_ERROR_STOP=1 -f -
    if ($LASTEXITCODE -ne 0) {
        throw "$errorMessage (exit code $LASTEXITCODE)"
    }
}

function Ensure-FlywayHistoryTable() {
    $sql = @'
CREATE TABLE IF NOT EXISTS flyway_schema_history (
    installed_rank INT PRIMARY KEY,
    version VARCHAR(50) NULL,
    description VARCHAR(200) NULL,
    installed_on TIMESTAMP NOT NULL DEFAULT NOW(),
    success BOOLEAN NOT NULL DEFAULT TRUE
);
'@
    Invoke-DbSql -sql $sql -errorMessage "Failed to ensure flyway_schema_history exists"
}

function Get-AppliedMigrationVersions() {
    $query = "SELECT version FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL ORDER BY installed_rank;"
    $raw = & docker exec -i -e "PGPASSWORD=$env:SA_DB_PASSWORD" goody-postgres `
        psql -U Boss -d shoppingassistant -t -A -v ON_ERROR_STOP=1 -c $query
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to read flyway_schema_history (exit code $LASTEXITCODE)"
    }

    $versions = @()
    foreach ($line in $raw) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -gt 0) {
            $versions += $trimmed
        }
    }
    return $versions
}

function Register-Migration([string]$version, [string]$description) {
    $sql = @"
WITH next_rank AS (
    SELECT COALESCE(MAX(installed_rank), 0) + 1 AS installed_rank
    FROM flyway_schema_history
)
INSERT INTO flyway_schema_history (installed_rank, version, description, success)
SELECT next_rank.installed_rank, '$version', '$description', TRUE
FROM next_rank
WHERE NOT EXISTS (
    SELECT 1
    FROM flyway_schema_history
    WHERE version = '$version'
);
"@
    Invoke-DbSql -sql $sql -errorMessage "Failed to register migration V$version"
}

function Apply-Migration([pscustomobject]$migration) {
    if ($script:AppliedMigrationVersions -contains $migration.Version) {
        Write-Host "Skipping V$($migration.Version): already recorded in flyway_schema_history" -ForegroundColor DarkGray
        return
    }

    $migrationPath = Join-Path $ShoppingAssistantDir "server\src\main\resources\db\migration\$($migration.FileName)"
    if (-not (Test-Path -Path $migrationPath)) {
        throw "Migration file not found: $migrationPath"
    }

    $containerPath = "/tmp/$($migration.FileName)"
    Invoke-Checked { docker cp $migrationPath "goody-postgres:$containerPath" } "Failed to copy $($migration.FileName) into postgres container"
    Invoke-Checked {
        docker exec -e "PGPASSWORD=$env:SA_DB_PASSWORD" goody-postgres `
            psql -U Boss -d shoppingassistant -v ON_ERROR_STOP=1 -f $containerPath
    } "Failed to apply V$($migration.Version) in postgres"
    Register-Migration -version $migration.Version -description $migration.Description

    $script:AppliedMigrationVersions += $migration.Version
}

Ensure-Command "docker"

if (-not (Test-Path -Path $ShoppingAssistantDir)) {
    throw "ShoppingAssistantDir not found: $ShoppingAssistantDir"
}
if (-not (Test-Path -Path $ComposeDir)) {
    throw "ComposeDir not found: $ComposeDir"
}

if ($DbPassword.Trim().Length -gt 0) {
    $env:SA_DB_PASSWORD = $DbPassword
}

if (-not $env:SA_DB_PASSWORD) {
    throw "SA_DB_PASSWORD is not set. Pass -DbPassword or set env:SA_DB_PASSWORD first."
}

Step "Build server install distribution locally"
Set-Location $ShoppingAssistantDir
& .\gradlew.bat :server:installDist --no-daemon

$distCandidates = @(
    (Join-Path $ShoppingAssistantDir "build\server\install\server"),
    (Join-Path $ShoppingAssistantDir "server\build\install\server")
)
$distPath = $distCandidates | Where-Object { Test-Path -Path $_ } | Select-Object -First 1
if (-not $distPath) {
    throw "Missing installDist output. Checked: $($distCandidates -join ', ')"
}

Step "Build runtime image (without Gradle in container)"
$tempDockerfile = Join-Path $distPath "Dockerfile.runtime"
@"
FROM eclipse-temurin:21-jre
WORKDIR /opt/server
ENV APP_HOST=0.0.0.0
ENV APP_PORT=8081
COPY . /opt/server/
EXPOSE 8081
CMD ["/opt/server/bin/server"]
"@ | Set-Content -Path $tempDockerfile -Encoding UTF8

try {
    docker build -f $tempDockerfile -t goody-backend $distPath
} finally {
    if (Test-Path $tempDockerfile) {
        Remove-Item $tempDockerfile -Force
    }
}

Step "Ensure Postgres is up"
Set-Location $ComposeDir
Invoke-Checked { docker compose up -d postgres } "Failed to start postgres service"

Step "Apply tracked migrations (V15-V23) to Postgres"
Ensure-FlywayHistoryTable
$script:AppliedMigrationVersions = @(Get-AppliedMigrationVersions)
$trackedMigrations = @(
    [pscustomobject]@{ Version = "15"; Description = "catalog_stage4_contract"; FileName = "V15__catalog_stage4_contract.sql" }
    [pscustomobject]@{ Version = "16"; Description = "catalog_stage4_runtime_execution"; FileName = "V16__catalog_stage4_runtime_execution.sql" }
    [pscustomobject]@{ Version = "17"; Description = "catalog_stage4_typed_constraints"; FileName = "V17__catalog_stage4_typed_constraints.sql" }
    [pscustomobject]@{ Version = "18"; Description = "offers_geo_coalesce_index"; FileName = "V18__offers_geo_coalesce_index.sql" }
    [pscustomobject]@{ Version = "19"; Description = "tracks_target_spec_v2"; FileName = "V19__tracks_target_spec_v2.sql" }
    [pscustomobject]@{ Version = "20"; Description = "catalog_category_replacement"; FileName = "V20__catalog_category_replacement.sql" }
    [pscustomobject]@{ Version = "21"; Description = "catalog_stage20_taxonomy_sources"; FileName = "V21__catalog_stage20_taxonomy_sources.sql" }
    [pscustomobject]@{ Version = "22"; Description = "catalog_i18n_titles"; FileName = "V22__catalog_i18n_titles.sql" }
    [pscustomobject]@{ Version = "23"; Description = "catalog_category_title_ru"; FileName = "V23__catalog_category_title_ru.sql" }
)

foreach ($migration in $trackedMigrations) {
    Apply-Migration -migration $migration
}

Step "Recreate goody-backend container from compose"
Invoke-Checked { docker compose up -d --force-recreate --no-build goody-backend } "Failed to recreate goody-backend"

Step "Wait for backend health"
$health = Wait-HttpOk -url "http://127.0.0.1:8081/health" -maxAttempts 90 -sleepSeconds 2
Write-Host "health: $($health.StatusCode) $($health.Content)"

Step "Check catalog version endpoint"
$version = Wait-HttpOk -url "http://127.0.0.1:8081/api/catalog/version" -maxAttempts 30 -sleepSeconds 2
Write-Host "catalog/version: $($version.StatusCode) $($version.Content)"

Step "Check catalog categories endpoint"
$categories = Wait-HttpOk -url "http://127.0.0.1:8081/api/catalog/categories" -maxAttempts 30 -sleepSeconds 2
Write-Host "catalog/categories: $($categories.StatusCode)"

Step "Done"
Write-Host "goody-backend is up on http://127.0.0.1:8081" -ForegroundColor Green
