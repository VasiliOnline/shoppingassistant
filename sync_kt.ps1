# ===========================
# sync_kt.ps1  (UTF-8, no BOM) — Windows PowerShell 5.x
# Собирает *.kt в project_files/, добавляет // Last synced: <ts>,
# пишет date_and_time.txt, делает ZIP + project_files_ready.zip,
# ведёт лог, сравнивает по нормализованному тексту
# и шлёт изменения на FastAPI /bulk (JSON с файлами и delete).
# ===========================

# --- Console encoding ---
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

# --- Settings ---
# Корень проекта: по умолчанию текущая папка (start.ps1 уже запускает здесь)
$sourcePath       = (Get-Location).Path
$projectFilesPath = Join-Path $sourcePath "project_files"
$metaPath         = Join-Path $sourcePath "meta"
$zipPath          = Join-Path $sourcePath "project_files.zip"
$readyZipPath     = Join-Path $sourcePath "project_files_ready.zip"
$dateTimeFile     = Join-Path $projectFilesPath "date_and_time.txt"
$logFile          = Join-Path $metaPath "sync_log.txt"
$stateFile        = Join-Path $metaPath "state.json"

$maxIterations    = 10000   # Сколько итераций крутить
$intervalSeconds  = 5       # Пауза между итерациями
$oneShot          = $false  # Если $true — сделает одну итерацию и выйдет

# --- BULK SETTINGS (FastAPI main.py) ---
$bulkEnabled = $true

# Локальный URL до твоего FastAPI-контейнера
# Если docker-compose публикует 8000 наружу:
$bulkUrl     = "http://localhost:8000/bulk"
# Если хочешь бить через Cloudflare:
# $bulkUrl  = "https://files.goodstracker.ru/bulk"

# Должен совпадать с ARCHIVE_API_KEY в контейнере
$bulkApiKey  = "my_super_secret_123"
# ---------------------------------------

# Какие модули участвуют в синке исходников
$syncModuleNames = @("app", "core", "feature", "domain", "server", "rank")

# --- Ensure folders exist ---
foreach ($p in @($projectFilesPath, $metaPath)) {
    if (-not (Test-Path $p)) {
        New-Item -ItemType Directory -Path $p | Out-Null
    }
}

# --- Utils ---
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

# Нормализация текста: убираем BOM + стартовые // Last synced,
# выравниваем переводы строк, trim хвостов, NBSP → обычный пробел.
function Get-NormalizedText {
    param([Parameter(Mandatory = $true)][string]$Path)

    $text = Read-Utf8Strict -Path $Path

    # Удаляем BOM (если есть)
    if ($text.Length -gt 0 -and $text[0] -eq [char]0xFEFF) {
        $text = $text.Substring(1)
    }

    # CRLF/CR -> LF
    $text = $text -replace "`r`n?", "`n"

    # Убираем все ПОДРЯД стартовые строки // Last synced: ...
    $lines = $text -split "`n"
    while ($lines.Count -gt 0 -and ($lines[0] -match '^\s*//\s*Last\s+synced:')) {
        if ($lines.Count -gt 1) {
            $lines = $lines[1..($lines.Count - 1)]
        } else {
            $lines = @()
        }
    }

    # Unicode NFC
    $text = ($lines -join "`n").Normalize([System.Text.NormalizationForm]::FormC)

    # NBSP -> обычный пробел
    $text = $text -replace [char]0x00A0, ' '

    # Trim хвостов на каждой строке
    $lines = $text -split "`n"
    $lines = $lines | ForEach-Object { $_.TrimEnd(" ", "`t", [char]0x00A0) }

    # Убираем пустые строки в конце файла
    while ($lines.Count -gt 0 -and [string]::IsNullOrWhiteSpace($lines[-1])) {
        if ($lines.Count -gt 1) {
            $lines = $lines[0..($lines.Count - 2)]
        } else {
            $lines = @()
        }
    }

    return ($lines -join "`n")
}

# Хэш по нормализованному тексту
function Get-HashForCompare {
    param([Parameter(Mandatory = $true)][string]$Path)
    $norm  = Get-NormalizedText -Path $Path
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($norm)
    $sha   = [System.Security.Cryptography.SHA256]::Create()
    ($sha.ComputeHash($bytes) | ForEach-Object { $_.ToString("x2") }) -join ""
}

# Build pretty safe name (ui_navigation_NavGraph.kt и т.п.)
function Get-PathKey {
    param([Parameter(Mandatory = $true)][string]$Text)
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text.ToLowerInvariant())
    $sha1  = [System.Security.Cryptography.SHA1]::Create()
    (($sha1.ComputeHash($bytes) | ForEach-Object { $_.ToString("x2") }) -join "").Substring(0, 6)
}

function Build-PrettyNameCore {
    param([Parameter(Mandatory = $true)][string]$RelWithRoot)

    $parts = $RelWithRoot -split '[\\/]'
    $file  = $parts[-1]
    $dirs  = @()
    if ($parts.Length -gt 2) {
        $dirs = $parts[1..($parts.Length - 2)]
    }

    $base = [System.IO.Path]::GetFileNameWithoutExtension($file)
    $ext  = [System.IO.Path]::GetExtension($file)

    $tail = @()
    if     ($dirs.Count -ge 2) { $tail = $dirs[($dirs.Count - 2)..($dirs.Count - 1)] }
    elseif ($dirs.Count -eq 1) { $tail = @($dirs[-1]) }

    $dirTokens = $tail | ForEach-Object {
        $t = $_.ToLowerInvariant()
        $t = ($t -replace '[^\p{L}\p{Nd}\s-]', '') -replace '\s+', '_' -replace '-', '_'
        if ([string]::IsNullOrWhiteSpace($t)) { '_' } else { $t }
    }

    $dirPart = ($dirTokens -join "_")
    if (-not [string]::IsNullOrWhiteSpace($dirPart)) {
        $name = "${dirPart}_${base}${ext}"
    } else {
        $name = "${base}${ext}"
    }

    # final sanitize: allow letters/digits/_/.- ; others -> _
    return ($name -replace '[^\p{L}\p{Nd}_\.\-]', '_')
}

function Get-PrettySafeName {
    param(
        [Parameter(Mandatory = $true)][string]$RelWithRoot,
        [Parameter(Mandatory = $true)][hashtable]$UsedNames
    )

    $core = Build-PrettyNameCore -RelWithRoot $RelWithRoot

    if (-not $UsedNames.ContainsKey($core)) {
        $UsedNames[$core] = $RelWithRoot
        return $core
    }
    if ($UsedNames[$core] -eq $RelWithRoot) {
        return $core
    }

    # collision -> append short key
    $key  = Get-PathKey -Text $RelWithRoot
    $base = [System.IO.Path]::GetFileNameWithoutExtension($core)
    $ext  = [System.IO.Path]::GetExtension($core)
    $alt  = "${base}__${key}${ext}"

    if ($UsedNames.ContainsKey($alt) -and $UsedNames[$alt] -ne $RelWithRoot) {
        $key2 = Get-PathKey -Text ($RelWithRoot + '|2')
        $alt  = "${base}__${key}_${key2}${ext}"
    }

    $UsedNames[$alt] = $RelWithRoot
    return $alt
}

# Исключение путей (build/.gradle/.idea/.git/kapt/ksp/out и т.п.)
function Is-Excluded {
    param([Parameter(Mandatory = $true)][string]$FullPath)
    $p = $FullPath.ToLowerInvariant()
    return (
        $p -like "*\project_files\*" -or
        $p -like "*\meta\*"          -or
        $p -like "*\build\*"         -or
        $p -like "*\.gradle\*"       -or
        $p -like "*\.gradle-local\*" -or
        $p -like "*\.android-local\*" -or
        $p -like "*\.kotlin\*"       -or
        $p -like "*\.idea\*"         -or
        $p -like "*\.git\*"          -or
        $p -like "*\out\*"           -or
        $p -like "*\kapt\*"          -or
        $p -like "*\ksp\*"
    )
}

# В синк идут только .kt из подключённых модулей и не из test source sets.
function Is-SyncEligibleKt {
    param([Parameter(Mandatory = $true)][string]$FullPath)

    $rel = $FullPath.Substring($sourcePath.Length).TrimStart('\', '/')
    $rel = ($rel -replace '[\\/]+', '/')
    $mods = ($syncModuleNames -join "|")

    # Разрешаем только module/src/**.kt, исключая test source sets.
    $rx = "^(?:$mods)/src/(?!test/|androidTest/|jvmTest/|commonTest/|integrationTest/).+\.kt$"
    return ($rel -match $rx)
}

# ZIP (атомарно) + готовый zip
function Rebuild-ProjectFilesZips {
    param(
        [Parameter(Mandatory = $true)][string]$FolderPath,
        [Parameter(Mandatory = $true)][string]$ZipPath,
        [Parameter(Mandatory = $true)][string]$ReadyZipPath
    )

    Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue | Out-Null

    $tmpZip  = Join-Path ([System.IO.Path]::GetDirectoryName($ZipPath))      "project_files.tmp.zip"
    $tmpZip2 = Join-Path ([System.IO.Path]::GetDirectoryName($ReadyZipPath)) "project_files_ready.tmp.zip"

    foreach ($t in @($tmpZip, $tmpZip2)) {
        if (Test-Path $t) {
            Remove-Item -LiteralPath $t -Force -ErrorAction SilentlyContinue
        }
    }

    if (Test-Path $FolderPath) {
        [System.IO.Compression.ZipFile]::CreateFromDirectory(
            $FolderPath, $tmpZip,
            [System.IO.Compression.CompressionLevel]::Optimal,
            $false
        )
        [System.IO.Compression.ZipFile]::CreateFromDirectory(
            $FolderPath, $tmpZip2,
            [System.IO.Compression.CompressionLevel]::Optimal,
            $false
        )

        if (Test-Path $ZipPath)      { Remove-Item -LiteralPath $ZipPath      -Force -ErrorAction SilentlyContinue }
        if (Test-Path $ReadyZipPath) { Remove-Item -LiteralPath $ReadyZipPath -Force -ErrorAction SilentlyContinue }

        Move-Item -LiteralPath $tmpZip  -Destination $ZipPath      -Force
        Move-Item -LiteralPath $tmpZip2 -Destination $ReadyZipPath -Force
    } else {
        if (Test-Path $ZipPath)      { Remove-Item -LiteralPath $ZipPath      -Force -ErrorAction SilentlyContinue }
        if (Test-Path $ReadyZipPath) { Remove-Item -LiteralPath $ReadyZipPath -Force -ErrorAction SilentlyContinue }
    }
}

# POST /bulk на FastAPI (JSON с date/files/delete) — "боевой" тихий вариант
function Send-BulkUpdate {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)][string]$ApiKey,
        [Parameter(Mandatory = $true)][string]$DateIso,
        [Parameter(Mandatory = $true)][System.Collections.IEnumerable]$Files,
        [Parameter(Mandatory = $true)][System.Collections.IEnumerable]$Deletes
    )

    # нормальные массивы
    $fileArr = @($Files)
    $delArr  = @($Deletes)

    if ($fileArr.Count -eq 0 -and $delArr.Count -eq 0) {
        return
    }

    $payload = @{
        date   = $DateIso
        files  = $fileArr
        delete = $delArr
    }

    $json = $payload | ConvertTo-Json -Depth 5 -Compress

    $headers = @{
        "x-api-key" = $ApiKey
    }

    try {
        # В консоль только короткое сообщение
        Write-Host ("BULK → {0} (files={1}, delete={2})" -f $Url, $fileArr.Count, $delArr.Count) -ForegroundColor Cyan

        $resp = Invoke-RestMethod -Uri $Url `
                                  -Method Post `
                                  -Headers $headers `
                                  -ContentType "application/json; charset=utf-8" `
                                  -Body $json

        # В лог записываем подробный ответ
        $respText = if ($resp -is [string]) { $resp } else { ($resp | ConvertTo-Json -Compress) }
        $msg = "{0} BULK OK: {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $respText
        Add-Content -Path $logFile -Value $msg

        # В консоль — коротко
        Write-Host "BULK OK" -ForegroundColor Magenta
    } catch {
        $ex  = $_.Exception
        $err = "{0} BULK ERROR: {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $ex.Message
        Write-Host $err -ForegroundColor Red
        Add-Content -Path $logFile -Value $err

        # Если сервер вернул тело с detail — только в лог, не в консоль
        $respObj = $ex.Response
        if ($respObj -and $respObj.GetResponseStream) {
            try {
                $reader = New-Object System.IO.StreamReader($respObj.GetResponseStream())
                $body   = $reader.ReadToEnd()
                if (-not [string]::IsNullOrWhiteSpace($body)) {
                    Add-Content -Path $logFile -Value ("{0} BULK ERROR BODY: {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $body)
                }
            } catch {}
        }
    }
}


# --- Start banner ---
Write-Host "sync_kt.ps1 started" -ForegroundColor Green
Write-Host "SourcePath: $sourcePath"
Write-Host "Output: $projectFilesPath | $zipPath | $readyZipPath"
Write-Host "Bulk: $($bulkUrl) (enabled=$bulkEnabled)"

# --- Main loop ---
for ($i = 1; $i -le $maxIterations; $i++) {

    $changes     = @()
    $bulkFiles   = @()
    $bulkDeletes = @()

    # загружаем предыдущий state, чтобы знать Path по SafeName для delete
    $prevStateIndex = @{}
    if (Test-Path $stateFile) {
        try {
            $rawState = Get-Content -LiteralPath $stateFile -Raw
            if (-not [string]::IsNullOrWhiteSpace($rawState)) {
                $prevState = $rawState | ConvertFrom-Json
                if ($prevState -is [System.Collections.IEnumerable]) {
                    foreach ($item in $prevState) {
                        if ($item -and $item.SafeName -and $item.Path) {
                            $prevStateIndex[$item.SafeName] = $item
                        }
                    }
                } elseif ($prevState.SafeName -and $prevState.Path) {
                    $prevStateIndex[$prevState.SafeName] = $prevState
                }
            }
        } catch {
            # глючный state просто игнорируем
        }
    }

    # 1) Собираем список исходников *.kt (исключая служебные/сгенерённые)
    $sourceKts = Get-ChildItem -Path $sourcePath -Recurse -File -Filter *.kt |
                 Where-Object {
                     -not (Is-Excluded -FullPath $_.FullName) -and
                     (Is-SyncEligibleKt -FullPath $_.FullName)
                 }

    $rootName  = Split-Path $sourcePath -Leaf
$usedNames = @{}   # prettyName -> RelWithRoot (для имени в project_files)

$currentState = foreach ($f in $sourceKts) {
    # относительный путь от корня проекта
    $rel = $f.FullName.Substring($sourcePath.Length).TrimStart('\', '/')

    # то, что уйдёт на сервер: только '/', без имени корня
    # пример: "feature/src/main/java/..."
    $relForServer = ($rel -replace '[\\/]+', '/')

    # а для красивого имени файла в project_files продолжаем учитывать имя корня
    $relWithRoot  = ($rootName.Trim('\', '/')) + '\' + ($rel -replace '[\\/]+', '\')

    [PSCustomObject]@{
        Path     = $relForServer      # ← ЭТО уйдёт в JSON → FastAPI
        SafeName = Get-PrettySafeName -RelWithRoot $relWithRoot -UsedNames $usedNames
        Hash     = Get-HashForCompare -Path $f.FullName
        Source   = $f.FullName
    }
}



    # 2) Текущее состояние архива project_files/
    $archiveState = Get-ChildItem -Path $projectFilesPath -File -Filter *.kt | ForEach-Object {
        [PSCustomObject]@{
            SafeName = $_.Name
            Hash     = Get-HashForCompare -Path $_.FullName
            File     = $_.FullName
        }
    }

    $currIndex = @{}
    foreach ($c in $currentState) { $currIndex[$c.SafeName] = $c }

    $archIndex = @{}
    foreach ($a in $archiveState) { $archIndex[$a.SafeName] = $a }

    # 3) Add / Modify
    foreach ($file in $currentState) {
        $destFilePath = Join-Path $projectFilesPath $file.SafeName

        if (-not $archIndex.ContainsKey($file.SafeName)) {
            $msg = "{0} ADDED: {1} -> {2}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $file.Path, $file.SafeName
            $changes += $msg

            $body = Read-Utf8Strict -Path $file.Source
            if ($body -match '^\s*//\s*Last\s+synced:') {
                $body = ($body -replace '^\s*//\s*Last\s+synced:.*?(\r?\n|$)', '')
            }
            $final = ("// Last synced: {0}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')) +
                     "`n" +
                     ($body -replace "`r`n?", "`n")
            Write-Utf8NoBom -Path $destFilePath -Text $final

            # для FastAPI: кладём уже готовый текст в /bulk
           $bulkFiles += @{
    path = $file.Path
    text = $final
}


        } elseif ($archIndex[$file.SafeName].Hash -ne $file.Hash) {

            # Диагностика нормализованного текста (останется как раньше)
            try {
                $debugDir = Join-Path $metaPath "diff_debug"
                if (-not (Test-Path $debugDir)) {
                    New-Item -ItemType Directory -Path $debugDir | Out-Null
                }

                $base    = [System.IO.Path]::GetFileNameWithoutExtension($file.SafeName)
                $srcNorm = Get-NormalizedText -Path $file.Source
                $dstNorm = ""
                if (Test-Path $destFilePath) {
                    $dstNorm = Get-NormalizedText -Path $destFilePath
                }

                Write-Utf8NoBom -Path (Join-Path $debugDir "$base.src.txt") -Text $srcNorm
                Write-Utf8NoBom -Path (Join-Path $debugDir "$base.dst.txt") -Text $dstNorm

                if ($null -eq $srcNorm) { $srcLen = 0 } else { $srcLen = $srcNorm.Length }
                if ($null -eq $dstNorm) { $dstLen = 0 } else { $dstLen = $dstNorm.Length }

                $srcHash = Get-HashForCompare -Path $file.Source
                if (Test-Path $destFilePath) {
                    $dstHash = Get-HashForCompare -Path $destFilePath
                } else {
                    $dstHash = ""
                }

                Write-Host ("MISMATCH: {0} srcHash={1} dstHash={2} srcLen={3} dstLen={4}" -f `
                    $file.SafeName, $srcHash, $dstHash, $srcLen, $dstLen) -ForegroundColor Yellow
            } catch {
                Write-Host ("DIFF DEBUG ERROR: " + $_.Exception.Message) -ForegroundColor Red
            }

            $msg = "{0} MODIFIED: {1} -> {2}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $file.Path, $file.SafeName
            $changes += $msg

            $body = Read-Utf8Strict -Path $file.Source
            if ($body -match '^\s*//\s*Last\s+synced:') {
                $body = ($body -replace '^\s*//\s*Last\s+synced:.*?(\r?\n|$)', '')
            }
            $final = ("// Last synced: {0}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')) +
                     "`n" +
                     ($body -replace "`r`n?", "`n")
            Write-Utf8NoBom -Path $destFilePath -Text $final

            $bulkFiles += [PSCustomObject]@{
                path = $file.Path
                text = $final
            }
        }
    }

    # 4) Delete
    foreach ($arch in $archiveState) {
        if (-not $currIndex.ContainsKey($arch.SafeName)) {
            $msg = "{0} DELETED: {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $arch.SafeName
            $changes += $msg
            Remove-Item -LiteralPath $arch.File -Force -ErrorAction SilentlyContinue

            # для FastAPI нужно знать исходный Path → берём из prevStateIndex
            if ($prevStateIndex.ContainsKey($arch.SafeName)) {
                $pathForDelete = $prevStateIndex[$arch.SafeName].Path
                if ($pathForDelete) {
                    $bulkDeletes += $pathForDelete
                }
            }
        }
    }

    # 5) Logging + date/time + ZIP + bulk
    if ($changes.Count -gt 0) {
        foreach ($c in $changes) {
            Write-Host $c -ForegroundColor Yellow
            Add-Content -Path $logFile -Value $c
        }

        # date_and_time.txt (локально, просто для отладки; серверу дату отправим через /bulk)
        $nowIso = Get-Date -Format "yyyy-MM-ddTHH:mm"
        try {
            Write-Utf8NoBom -Path $dateTimeFile -Text $nowIso
            $dateMsg = "{0} DATE UPDATED (local): {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $nowIso
            Write-Host $dateMsg -ForegroundColor Green
            Add-Content -Path $logFile -Value $dateMsg
        } catch {
            $err = "{0} DATE ERROR: {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $_.Exception.Message
            Write-Host $err -ForegroundColor Red
            Add-Content -Path $logFile -Value $err
        }

        # ZIPs (локально — как раньше)
        try {
            Rebuild-ProjectFilesZips -FolderPath $projectFilesPath -ZipPath $zipPath -ReadyZipPath $readyZipPath
            $zipMsg = "{0} ZIP UPDATED: {1} (and {2})" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $zipPath, $readyZipPath
            Write-Host $zipMsg -ForegroundColor Cyan
            Add-Content -Path $logFile -Value $zipMsg
        } catch {
            $err = "{0} ZIP ERROR: {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $_.Exception.Message
            Write-Host $err -ForegroundColor Red
            Add-Content -Path $logFile -Value $err
        }

        # /bulk → FastAPI
        if ($bulkEnabled -and -not [string]::IsNullOrWhiteSpace($bulkUrl)) {
            Send-BulkUpdate -Url $bulkUrl -ApiKey $bulkApiKey -DateIso $nowIso -Files $bulkFiles -Deletes $bulkDeletes
        }
    }

    # 6) Persist state snapshot (Path/SafeName/Hash/Source)
    try {
        $currentState | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $stateFile -Encoding UTF8
    } catch {
        $err = "{0} STATE ERROR: {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $_.Exception.Message
        Write-Host $err -ForegroundColor Red
        Add-Content -Path $logFile -Value $err
    }

    if ($oneShot) { break }

    Start-Sleep -Seconds $intervalSeconds
}
