param(
    # Команда: log | show | revert
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet("log", "show", "revert")]
    [string]$Command,

    # Для log: фильтр по части пути
    # Для show/revert: либо относительный путь "feature/src/.../File.kt",
    # либо числовой ID файла (из лога), например "12"
    [Parameter(Position = 1)]
    [string]$Target = "",

    # Для show: номер ревизии (0 = показать текущий файл из проекта)
    [Parameter(Position = 2)]
    [int]$Rev = 0,

    # Для log: сколько последних записей
    [int]$Last = 50,

    # Для log: фильтр по gpt_request_id
    [string]$Request = "",

    # Для revert: на сколько шагов откатить (1 = предыдущая ревизия)
    [int]$Steps = 1
)

try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

# Предполагаем запуск из корня проекта
$ProjectRoot  = (Get-Location).Path
$MetaRoot     = Join-Path $ProjectRoot ".gg_meta"
$ChangesRoot  = Join-Path $MetaRoot "changes"
$LogFile      = Join-Path $MetaRoot "changes_log.jsonl"
$StateFile    = Join-Path $MetaRoot "state.json"
$FileIndex    = Join-Path $MetaRoot "files_index.json"

function Write-Utf8NoBom {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Text
    )
    $enc = New-Object System.Text.UTF8Encoding($false, $true)
    [System.IO.File]::WriteAllText($Path, $Text, $enc)
}

function Read-Utf8Strict {
    param([Parameter(Mandatory = $true)][string]$Path)
    $enc = New-Object System.Text.UTF8Encoding($false, $true)
    return [System.IO.File]::ReadAllText($Path, $enc)
}

function Get-RelChangesDir {
    param([Parameter(Mandatory = $true)][string]$RelPath)

    $relPathNorm = $RelPath -replace '[\\/]+', '/'
    $relDir      = Split-Path $relPathNorm -Parent
    $fileName    = Split-Path $relPathNorm -Leaf

    if ([string]::IsNullOrWhiteSpace($relDir)) {
        return (Join-Path $ChangesRoot $fileName)
    } else {
        $dirForFile = Join-Path $ChangesRoot $relDir
        return (Join-Path $dirForFile $fileName)
    }
}

function Load-FileIndex {
    if (-not (Test-Path $FileIndex)) {
        return @()
    }
    $raw = Get-Content -LiteralPath $FileIndex -Raw
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return @()
    }
    $parsed = $raw | ConvertFrom-Json
    if ($parsed -is [System.Collections.IEnumerable]) {
        return @($parsed)
    } else {
        return @($parsed)
    }
}

function Get-RelPathByFileId {
    param([Parameter(Mandatory = $true)][int]$FileId)

    $list = Load-FileIndex
    if (-not $list) { return $null }

    $item = $list | Where-Object { $_.id -eq $FileId } | Select-Object -First 1
    if (-not $item) { return $null }
    return $item.file
}

function Show-Log {
    param(
        [int]$Last        = 50,
        [string]$FileMask = "",
        [string]$Req      = ""
    )

    if (-not (Test-Path $LogFile)) {
        Write-Host "Лог изменений не найден: $LogFile" -ForegroundColor Red
        return
    }

    $lines = Get-Content -Path $LogFile -ErrorAction SilentlyContinue
    if (-not $lines) {
        Write-Host "Лог пуст." -ForegroundColor Yellow
        return
    }

    if ($Last -gt 0) {
        $lines = $lines | Select-Object -Last $Last
    }

    foreach ($ln in $lines) {
        if ([string]::IsNullOrWhiteSpace($ln)) { continue }

        try {
            $obj = $ln | ConvertFrom-Json
        } catch {
            continue
        }

        if ($FileMask -and ($obj.file -notlike "*$FileMask*")) { continue }
        if ($Req -and ($obj.gpt_request_id -ne $Req)) { continue }

        $ts    = $obj.timestamp
        $op    = $obj.operation
        $file  = $obj.file
        $la    = $obj.lines_added
        $lr    = $obj.lines_removed
        $from  = $obj.from_rev
        $to    = $obj.to_rev
        $reqId = $obj.gpt_request_id
        $fid   = $obj.file_id

        $line = "[#{0}] [{1}] {2,-7} {3}  +{4}/-{5}  rev {6}->{7}" -f `
            $fid, $ts, ($op.ToUpper()), $file, $la, $lr, $from, $to

        if ($reqId) {
            $line += "  req=$reqId"
        }

        Write-Host $line -ForegroundColor Cyan
    }
}

function Load-StateEntry {
    param([Parameter(Mandatory = $true)][string]$RelPath)

    if (-not (Test-Path $StateFile)) {
        return $null
    }

    $stateJson = Get-Content -LiteralPath $StateFile -Raw
    if ([string]::IsNullOrWhiteSpace($stateJson)) {
        return $null
    }

    $state = $stateJson | ConvertFrom-Json
    if (-not $state) { return $null }

    if ($state -is [System.Collections.IEnumerable]) {
        return ($state | Where-Object { $_.RelPath -eq $RelPath } | Select-Object -First 1)
    } elseif ($state.RelPath -eq $RelPath) {
        return $state
    }
    return $null
}

function Show-Revision {
    param(
        [Parameter(Mandatory = $true)][string]$RelPath,
        [int]$Rev = 0
    )

    $entry = Load-StateEntry -RelPath $RelPath
    if (-not $entry) {
        Write-Host "Файл $RelPath не найден в state.json" -ForegroundColor Red
        return
    }

    $currentRev = [int]$entry.Rev
    $ext        = if ($entry.Ext) { $entry.Ext } else { [System.IO.Path]::GetExtension($RelPath) }

    if ($Rev -le 0) {
        # Показать текущую версию из проекта
        $fullPath = Join-Path $ProjectRoot ($RelPath -replace '/', '\')
        if (-not (Test-Path $fullPath)) {
            Write-Host "Текущий файл в проекте не найден: $fullPath" -ForegroundColor Red
            return
        }
        Write-Host "=== CURRENT: $RelPath (rev=$currentRev) ===" -ForegroundColor Yellow
        Get-Content -LiteralPath $fullPath
        return
    }

    $chgDir = Get-RelChangesDir -RelPath $RelPath
    if (-not (Test-Path $chgDir)) {
        Write-Host "Каталог изменений не найден: $chgDir" -ForegroundColor Red
        return
    }

    $fileName = ("{0:D4}_after{1}" -f $Rev, $ext)
    $snapPath = Join-Path $chgDir $fileName

    if (-not (Test-Path $snapPath)) {
        Write-Host "Снапшот ревизии не найден: $snapPath" -ForegroundColor Red
        return
    }

    Write-Host "=== REV $Rev: $RelPath ===" -ForegroundColor Yellow
    Get-Content -LiteralPath $snapPath
}

function Revert-File {
    param(
        [Parameter(Mandatory = $true)][string]$RelPath,
        [int]$Steps = 1
    )

    $entry = Load-StateEntry -RelPath $RelPath
    if (-not $entry) {
        Write-Host "Файл $RelPath не найден в state.json (возможно, он был удалён). Пока откат для таких кейсов не реализован." -ForegroundColor Red
        return
    }

    $currentRev = if ($entry.Rev) { [int]$entry.Rev } else { 0 }
    if ($currentRev -lt 1) {
        Write-Host "У файла $RelPath нет ревизий для отката (Rev=0)." -ForegroundColor Yellow
        return
    }

    $targetRev = $currentRev - [math]::Abs($Steps)
    if ($targetRev -lt 1) { $targetRev = 1 }

    $ext    = if ($entry.Ext) { $entry.Ext } else { [System.IO.Path]::GetExtension($RelPath) }
    $chgDir = Get-RelChangesDir -RelPath $RelPath
    if (-not (Test-Path $chgDir)) {
        Write-Host "Каталог изменений не найден: $chgDir" -ForegroundColor Red
        return
    }

    $snapName = ("{0:D4}_after{1}" -f $targetRev, $ext)
    $snapPath = Join-Path $chgDir $snapName

    if (-not (Test-Path $snapPath)) {
        Write-Host "Снапшот целевой ревизии не найден: $snapPath" -ForegroundColor Red
        return
    }

    $content  = Read-Utf8Strict -Path $snapPath
    $fullPath = Join-Path $ProjectRoot ($RelPath -replace '/', '\')

    Write-Host "Откат: $RelPath  rev $currentRev -> $targetRev" -ForegroundColor Yellow
    Write-Host "Записываем в файл: $fullPath" -ForegroundColor Yellow

    Write-Utf8NoBom -Path $fullPath -Text $content

    Write-Host "Откат выполнен. Watcher на следующей итерации увидит изменение и создаст новую ревизию." -ForegroundColor Green
}

# --- Router ---
switch ($Command) {
    "log" {
        Show-Log -Last $Last -FileMask $Target -Req $Request
        break
    }
    "show" {
        if (-not $Target) {
            Write-Host "Укажи путь или ID файла: .\gg_manager\ggctl.ps1 show feature/src/.../File.kt [rev] ИЛИ .\gg show 12 [rev]" -ForegroundColor Yellow
            break
        }

        $relPath = $Target
        if ($Target -match '^\d+$') {
            $id = [int]$Target
            $relPath = Get-RelPathByFileId -FileId $id
            if (-not $relPath) {
                Write-Host "Не найден файл с ID=$id в files_index.json" -ForegroundColor Red
                break
            }
        }

        Show-Revision -RelPath $relPath -Rev $Rev
        break
    }
    "revert" {
        if (-not $Target) {
            Write-Host "Укажи путь или ID файла: .\gg revert feature/src/.../File.kt  ИЛИ  .\gg revert 12" -ForegroundColor Yellow
            break
        }

        $relPath = $Target
        if ($Target -match '^\d+$') {
            $id = [int]$Target
            $relPath = Get-RelPathByFileId -FileId $id
            if (-not $relPath) {
                Write-Host "Не найден файл с ID=$id в files_index.json" -ForegroundColor Red
                break
            }
        }

        Revert-File -RelPath $relPath -Steps $Steps
        break
    }
}
