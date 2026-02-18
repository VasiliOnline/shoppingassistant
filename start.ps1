# start.ps1 - Goody launcher with Windows Terminal tabs
$ErrorActionPreference = "Stop"

# === SETTINGS ===

# FastAPI archive server path
$ArchiveServerPath   = "C:\Users\Asus\Desktop\archive_server"

# Android project root
$ShoppingProjectPath = "C:\Users\Asus\Desktop\Shoppingassistant"

# Cloudflare Tunnel
$TunnelName     = "goodstracker"
$CloudflaredExe = "cloudflared.exe"   # при необходимости можно указать полный путь

# Backend
$BackendCommand = "docker compose up -d --no-build --pull never goody-backend"
$BackendBuildCommand = "docker compose up --build -d goody-backend"

# Полный путь к Windows PowerShell 5.1
$PSExe = Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe"


# === HELPERS ===

function Start-WTTab {
    param(
        [string]$WorkingDir,
        [string]$InnerCommand   # строка, которую выполнит PowerShell в табе
    )

    Write-Host ""
    Write-Host "Starting WT tab in '$WorkingDir': $InnerCommand" -ForegroundColor Cyan

    if (-not (Test-Path $WorkingDir)) {
        Write-Host "Working directory not found: $WorkingDir" -ForegroundColor Red
        return
    }

    $wt = "wt.exe"

    # Если Windows Terminal не найден — открываем отдельное окно PowerShell
    if (-not (Get-Command $wt -ErrorAction SilentlyContinue)) {
        Write-Host "wt.exe not found, fallback to separate PowerShell window." -ForegroundColor Yellow
        Start-Process $PSExe `
            -WorkingDirectory $WorkingDir `
            -ArgumentList @("-NoProfile", "-NoExit", "-Command", $InnerCommand) | Out-Null
        return
    }

    # Без --title (чтобы WT не пытался запускать 'Manager -d ...' как команду).
    # Используем полный путь к PowerShell, чтобы не зависеть от PATH из WT.
    $args = @(
        "-w", "0",
        "new-tab",
        "-d", $WorkingDir,
        $PSExe,
        "-NoProfile",
        "-NoExit",
        "-Command",
        $InnerCommand
    )

    Start-Process $wt -ArgumentList $args | Out-Null
}


# === HANDLERS ===

function Start-Cloudflare {
    # cloudflared tunnel run <TunnelName>
    $inner = "$CloudflaredExe tunnel run $TunnelName"
    Start-WTTab -WorkingDir $ArchiveServerPath -InnerCommand $inner
}

function Start-SyncKt {
    $inner = ".\sync_kt.ps1"
    Start-WTTab -WorkingDir $ShoppingProjectPath -InnerCommand $inner
}

function Start-Backend {
    # Запускаем docker compose из папки с docker-compose.yml
    $inner = $BackendCommand
    Start-WTTab -WorkingDir $ArchiveServerPath -InnerCommand $inner
}

function Start-BackendBuild {
    # Запускаем docker compose с пересборкой образа
    $inner = $BackendBuildCommand
    Start-WTTab -WorkingDir $ArchiveServerPath -InnerCommand $inner
}

# Архив-сервер: запускаем uvicorn прямо в ЭТОЙ вкладке (как раньше),
# чтобы она показывала живой лог и была "серверной".
function Start-ArchiveServer {
    Write-Host ""
    Write-Host "[Archive] Starting FastAPI archive server in THIS tab (FOREGROUND)..." -ForegroundColor Cyan

    if (-not (Test-Path $ArchiveServerPath)) {
        Write-Host "archive_server folder not found: $ArchiveServerPath" -ForegroundColor Red
        return
    }
    # --- Ensure port 8000 is free (kill old uvicorn if still listening) ---
    try {
        $listeners = Get-NetTCPConnection -LocalPort 8000 -State Listen -ErrorAction Stop
        foreach ($l in $listeners) {
            if ($l.OwningProcess) {
                Write-Host "[Archive] Port 8000 is already used by PID $($l.OwningProcess). Stopping it..." -ForegroundColor Yellow
                Stop-Process -Id $l.OwningProcess -Force -ErrorAction SilentlyContinue
            }
        }
    } catch {
        # Fallback (if Get-NetTCPConnection isn't available)
        try {
            $hits = netstat -ano | Select-String -Pattern "[:.]8000\s+.*LISTENING\s+(\d+)$" -AllMatches
            foreach ($h in $hits) {
                foreach ($m in $h.Matches) {
                    $procId = [int]$m.Groups[1].Value
if ($procId -gt 0) {
    Write-Host "[Archive] Port 8000 is already used by PID $procId. Stopping it..." -ForegroundColor Yellow
    Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
}

                }
            }
        } catch {}
    }

    # Build marker to see which instance answered (useful if tunnel/cache)
    $env:ARCHIVE_BUILD_ID = (Get-Date -Format 'yyyyMMdd-HHmmss')

    Set-Location $ArchiveServerPath

    Write-Host "[Archive] Running: python -m uvicorn main:app --host 0.0.0.0 --port 8000" -ForegroundColor Green
    Write-Host "[Archive] Press CTRL+C here to stop archive server." -ForegroundColor Yellow

    # Блокирующий запуск uvicorn — логи будут идти прямо в эту вкладку
    python -m uvicorn main:app --host 0.0.0.0 --port 8000
}


# === MENU ===

Write-Host "=== Goody environment starter ===" -ForegroundColor White
Write-Host ""
Write-Host "1 - Archive server (FastAPI)  - THIS tab (logs, foreground)" -ForegroundColor White
Write-Host "2 - Cloudflare Tunnel         - new WT tab" -ForegroundColor White
Write-Host "3 - sync_kt.ps1 (archive)     - new WT tab" -ForegroundColor White
Write-Host "4 - Backend (run)             - new WT tab" -ForegroundColor White
Write-Host "5 - Backend (rebuild image)   - new WT tab" -ForegroundColor White
Write-Host ""

$inputStr = Read-Host "Enter handler numbers separated by space (example: 2 3 4)"

if ([string]::IsNullOrWhiteSpace($inputStr)) {
    Write-Host "Nothing selected. Exit." -ForegroundColor Yellow
    return
}

$choices = $inputStr -split '\s+' |
    Where-Object { $_ -match '^\d+$' } |
    Select-Object -Unique

# Сначала стартуем все фоновые вкладки (2/3/4), а уже ПОТОМ —
# превращаем текущую вкладку в серверную, если выбран пункт 1.
$runArchive = $false

foreach ($choice in $choices) {
    switch ($choice) {
        '1' { $runArchive = $true } # запомним, запустим после остальных
        '2' { Start-Cloudflare }
        '3' { Start-SyncKt }
        '4' { Start-Backend }
        '5' { Start-BackendBuild }
        default {
            Write-Host "Unknown handler number: $choice" -ForegroundColor Yellow
        }
    }
}

if ($runArchive) {
    Start-ArchiveServer
} else {
    Write-Host ""
    Write-Host "Done. Selected handlers started (if there were no errors)." -ForegroundColor White
}
