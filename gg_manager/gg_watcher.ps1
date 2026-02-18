# gg_watcher.ps1 — история изменений проекта (.gg_meta)
# - отслеживает файлы (kt/gradle/toml/json/...),
# - ведёт ревизии в .gg_meta/changes,
# - пишет лог в .gg_meta/changes_log.jsonl,
# - ведёт индекс файлов .gg_meta/files_index.json с file_id.

# Настройки трекаемых файлов (можно расширять)
if (-not $Global:GG_WatcherTrackedExtensions) {
    $Global:GG_WatcherTrackedExtensions = @(
        ".kt", ".kts",
        ".gradle",
        ".toml",
        ".json",
        ".xml",
        ".yml", ".yaml",
        ".properties",
        ".md",
        ".ps1", ".bat", ".sh"
    )
}

if (-not $Global:GG_WatcherTrackedNamesNoExt) {
    $Global:GG_WatcherTrackedNamesNoExt = @(
        "gradlew"
    )
}

function Initialize-GGWatcher {
    $Global:GG_WatcherMetaPath   = Join-Path $Global:GG_ProjectRoot ".gg_meta"
    $Global:GG_ChangesRoot       = Join-Path $Global:GG_WatcherMetaPath "changes"
    $Global:GG_ChangesLogFile    = Join-Path $Global:GG_WatcherMetaPath "changes_log.jsonl"
    $Global:GG_WatcherStateFile  = Join-Path $Global:GG_WatcherMetaPath "state.json"
    $Global:GG_FileIndexFile     = Join-Path $Global:GG_WatcherMetaPath "files_index.json"

    foreach ($p in @($Global:GG_WatcherMetaPath, $Global:GG_ChangesRoot)) {
        if (-not (Test-Path $p)) {
            New-Item -ItemType Directory -Path $p | Out-Null
        }
    }

    # Инициализация индекс-файла
    $Global:GG_FileIndexList = @()
    $Global:GG_FileIndexMap  = @{}
    $Global:GG_NextFileId    = 1

    if (Test-Path $Global:GG_FileIndexFile) {
        try {
            $raw = Get-Content -LiteralPath $Global:GG_FileIndexFile -Raw
            if (-not [string]::IsNullOrWhiteSpace($raw)) {
                $parsed = $raw | ConvertFrom-Json
                if ($parsed -is [System.Collections.IEnumerable]) {
                    $Global:GG_FileIndexList = @($parsed)
                } else {
                    $Global:GG_FileIndexList = @($parsed)
                }

                foreach ($item in $Global:GG_FileIndexList) {
                    if ($item -and $item.file -and $item.id) {
                        $Global:GG_FileIndexMap[$item.file] = $item
                    }
                }

                if ($Global:GG_FileIndexList.Count -gt 0) {
                    $maxId = ($Global:GG_FileIndexList | Measure-Object -Property id -Maximum).Maximum
                    if ($maxId -gt 0) {
                        $Global:GG_NextFileId = [int]$maxId + 1
                    }
                }
            }
        } catch {
            Write-Host "[GG-WATCHER] FILE INDEX READ ERROR: $($_.Exception.Message)" -ForegroundColor Red
        }
    }

    Write-Host "[GG-WATCHER] Initialized (.gg_meta, file index)" -ForegroundColor DarkCyan
}

function Is-GGTrackedFile {
    param([Parameter(Mandatory = $true)][System.IO.FileInfo]$File)

    $ext  = $File.Extension.ToLowerInvariant()
    $name = $File.Name.ToLowerInvariant()

    if ($Global:GG_WatcherTrackedExtensions -contains $ext) {
        return $true
    }
    if ($Global:GG_WatcherTrackedNamesNoExt -contains $name) {
        return $true
    }
    return $false
}

# Глобальный счётчик изменений в рамках процесса
if (-not $Global:GG_ChangeSequence) {
    $Global:GG_ChangeSequence = 0
}

function Save-GGFileIndex {
    try {
        $Global:GG_FileIndexList | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $Global:GG_FileIndexFile -Encoding UTF8
    } catch {
        Write-Host "[GG-WATCHER] FILE INDEX WRITE ERROR: $($_.Exception.Message)" -ForegroundColor Red
    }
}

function Get-GGFileIdForPath {
    param(
        [Parameter(Mandatory = $true)][string]$RelPath,
        [Parameter(Mandatory = $true)][int]$Rev
    )

    if ($Global:GG_FileIndexMap.ContainsKey($RelPath)) {
        $item = $Global:GG_FileIndexMap[$RelPath]
        $item.last_rev = $Rev
        Save-GGFileIndex
        return [int]$item.id
    }

    $id  = $Global:GG_NextFileId
    $now = Get-Date -Format "yyyy-MM-ddTHH:mm:ss"
    $Global:GG_NextFileId++

    $obj = [PSCustomObject]@{
        id       = $id
        file     = $RelPath
        created  = $now
        last_rev = $Rev
    }

    $Global:GG_FileIndexList += $obj
    $Global:GG_FileIndexMap[$RelPath] = $obj

    Save-GGFileIndex

    return $id
}

function GG-RegisterChange {
    param(
        [Parameter(Mandatory = $true)][string]$Operation,     # create | modify | delete
        [Parameter(Mandatory = $true)][string]$RelPath,       # "feature/src/.../MainPage.kt"
        [Parameter(Mandatory = $true)][int]$FromRev,
        [Parameter(Mandatory = $true)][int]$ToRev,
        [Parameter(Mandatory = $false)][string]$Ext,
        [Parameter(Mandatory = $false)][string]$CurrentContent
    )

    $Global:GG_ChangeSequence++
    $timestamp = Get-Date -Format "yyyy-MM-ddTHH:mm:ss"
    $idBase    = Get-Date -Format "yyyyMMddHHmmss"
    $changeId  = "chg-{0}-{1:D4}" -f $idBase, $Global:GG_ChangeSequence

    # Получаем/назначаем file_id
    $fileId = Get-GGFileIdForPath -RelPath $RelPath -Rev $ToRev

    $relPathNorm = $RelPath -replace '[\\/]+', '/'
    $relDir      = Split-Path $relPathNorm -Parent
    $fileName    = Split-Path $relPathNorm -Leaf

    if ([string]::IsNullOrWhiteSpace($relDir)) {
        $targetDir = Join-Path $Global:GG_ChangesRoot $fileName
    } else {
        $dirForFile = Join-Path $Global:GG_ChangesRoot $relDir
        $targetDir  = Join-Path $dirForFile $fileName
    }

    if (-not (Test-Path $targetDir)) {
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    }

    # предыдущее содержимое для diff и from_rev
    $prevContent = $null
    if ($FromRev -gt 0 -and $Operation -ne "create") {
        $extToUse     = if ($Ext) { $Ext } else { [System.IO.Path]::GetExtension($fileName) }
        $prevFileName = ("{0:D4}_after{1}" -f $FromRev, $extToUse)
        $prevPath     = Join-Path $targetDir $prevFileName
        if (Test-Path $prevPath) {
            $prevContent = Read-Utf8Strict -Path $prevPath
        }
    }

    if ($Operation -eq "create" -and -not $prevContent) {
        $prevContent = ""
    }

    $linesAdded   = 0
    $linesRemoved = 0

    if ($Operation -eq "create" -or $Operation -eq "modify") {
        $oldLines = @()
        $newLines = @()

        if ($prevContent -ne $null) {
            $oldLines = $prevContent -split "`r?`n"
        }
        if ($CurrentContent -ne $null) {
            $newLines = $CurrentContent -split "`r?`n"
        }

        $diff = Compare-Object -ReferenceObject $oldLines -DifferenceObject $newLines -IncludeEqual:$false
        foreach ($d in $diff) {
            if ($d.SideIndicator -eq "=>") { $linesAdded++ }
            elseif ($d.SideIndicator -eq "<=") { $linesRemoved++ }
        }

        # Снимок после изменения
        $extToUse = if ($Ext) { $Ext } else { [System.IO.Path]::GetExtension($fileName) }
        $afterFileName = ("{0:D4}_after{1}" -f $ToRev, $extToUse)
        $afterPath     = Join-Path $targetDir $afterFileName
        if ($CurrentContent -ne $null) {
            Write-Utf8NoBom -Path $afterPath -Text $CurrentContent
        }
    }

    # REQUEST_ID для связи с GPT (опционально)
    $gptRequestId = $env:GG_LAST_REQUEST_ID
    if ([string]::IsNullOrWhiteSpace($gptRequestId)) {
        $gptRequestId = $null
    }

    $meta = [ordered]@{
        id             = $changeId
        timestamp      = $timestamp
        file_id        = $fileId
        file           = $RelPath
        operation      = $Operation
        from_rev       = $FromRev
        to_rev         = $ToRev
        lines_added    = $linesAdded
        lines_removed  = $linesRemoved
        gpt_request_id = $gptRequestId
    }

    $metaJson     = $meta | ConvertTo-Json -Depth 5 -Compress
    $metaFileName = ("{0:D4}_meta.json" -f $ToRev)
    $metaPath     = Join-Path $targetDir $metaFileName
    Write-Utf8NoBom -Path $metaPath -Text $metaJson

    # Строкой в changes_log.jsonl
    $logLine = $meta | ConvertTo-Json -Compress
    Add-Content -Path $Global:GG_ChangesLogFile -Value $logLine

    # Кратко в консоль
    $summary = "[WATCHER] [#{0}] {1} {2} +{3}/-{4} rev {5}->{6}" -f `
        $fileId, $timestamp, $Operation.ToUpper(), $RelPath, $linesAdded, $linesRemoved, $FromRev, $ToRev
    Write-Host $summary -ForegroundColor Cyan
}

function Run-GGWatcherIteration {
    # 1) Читаем предыдущее состояние
    $prevStateMap = @{}   # RelPath -> { RelPath, Hash, Rev, Ext }

    if (Test-Path $Global:GG_WatcherStateFile) {
        try {
            $rawState = Get-Content -LiteralPath $Global:GG_WatcherStateFile -Raw
            if (-not [string]::IsNullOrWhiteSpace($rawState)) {
                $prevState = $rawState | ConvertFrom-Json
                if ($prevState -is [System.Collections.IEnumerable]) {
                    foreach ($item in $prevState) {
                        if ($item -and $item.RelPath) {
                            $prevStateMap[$item.RelPath] = $item
                        }
                    }
                } elseif ($prevState.RelPath) {
                    $prevStateMap[$prevState.RelPath] = $prevState
                }
            }
        } catch {
            Write-Host "[GG-WATCHER] STATE READ ERROR: $($_.Exception.Message)" -ForegroundColor Red
        }
    }

    # 2) Сканы текущих файлов
    $trackedFiles = Get-ChildItem -Path $Global:GG_ProjectRoot -Recurse -File |
                    Where-Object { -not (Is-Excluded -FullPath $_.FullName) -and (Is-GGTrackedFile -File $_) }

    $currentState  = @()
    $currentRelSet = New-Object System.Collections.Generic.HashSet[string]

    foreach ($f in $trackedFiles) {
        $rel  = Get-RelPathNormalized -FullPath $f.FullName
        $hash = Get-HashForCompare -Path $f.FullName
        $ext  = $f.Extension

        $prevRev = 0
        if ($prevStateMap.ContainsKey($rel) -and $prevStateMap[$rel].Rev) {
            $prevRev = [int]$prevStateMap[$rel].Rev
        }

        $obj = [PSCustomObject]@{
            RelPath = $rel
            Hash    = $hash
            Ext     = $ext
            Full    = $f.FullName
            PrevRev = $prevRev
        }

        $currentState  += $obj
        $null = $currentRelSet.Add($rel)
    }

    $newState  = @()
    $hasChange = $false

    # 3) Create / Modify
    foreach ($file in $currentState) {
        $rel    = $file.RelPath
        $hash   = $file.Hash
        $ext    = $file.Ext
        $full   = $file.Full
        $prevRev = $file.PrevRev

        if (-not $prevStateMap.ContainsKey($rel)) {
            # Новый файл
            $newRev  = 1
            $content = Read-Utf8Strict -Path $full
            GG-RegisterChange -Operation "create" -RelPath $rel -FromRev 0 -ToRev $newRev -Ext $ext -CurrentContent $content

            $newState += [PSCustomObject]@{
                RelPath = $rel
                Hash    = $hash
                Rev     = $newRev
                Ext     = $ext
            }
            $hasChange = $true
        } else {
            $prevEntry = $prevStateMap[$rel]
            $prevHash  = $prevEntry.Hash
            $prevRev   = if ($prevEntry.Rev) { [int]$prevEntry.Rev } else { 0 }
            $prevExt   = if ($prevEntry.Ext) { $prevEntry.Ext } else { $ext }

            if ($prevHash -ne $hash) {
                # Модификация
                $newRev  = $prevRev + 1
                $content = Read-Utf8Strict -Path $full
                GG-RegisterChange -Operation "modify" -RelPath $rel -FromRev $prevRev -ToRev $newRev -Ext $prevExt -CurrentContent $content

                $newState += [PSCustomObject]@{
                    RelPath = $rel
                    Hash    = $hash
                    Rev     = $newRev
                    Ext     = $prevExt
                }
                $hasChange = $true
            } else {
                # Без изменений
                $newState += [PSCustomObject]@{
                    RelPath = $rel
                    Hash    = $hash
                    Rev     = $prevRev
                    Ext     = $prevExt
                }
            }
        }
    }

    # 4) Delete
    foreach ($key in $prevStateMap.Keys) {
        if (-not $currentRelSet.Contains($key)) {
            $prevEntry = $prevStateMap[$key]
            $prevRev   = if ($prevEntry.Rev) { [int]$prevEntry.Rev } else { 0 }
            $prevExt   = $prevEntry.Ext

            $newRev = $prevRev + 1
            GG-RegisterChange -Operation "delete" -RelPath $key -FromRev $prevRev -ToRev $newRev -Ext $prevExt -CurrentContent $null
            $hasChange = $true
        }
    }

    # 5) Сохраняем новое состояние
    try {
        $newState | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $Global:GG_WatcherStateFile -Encoding UTF8
    } catch {
        Write-Host "[GG-WATCHER] STATE WRITE ERROR: $($_.Exception.Message)" -ForegroundColor Red
    }

    if (-not $hasChange) {
        return
    }
}
